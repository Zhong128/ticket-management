-- This migration deliberately does not change stock, financial amounts, or delete history.
UPDATE order_info SET expire_time=DATE_ADD(create_time,INTERVAL 30 MINUTE) WHERE expire_time IS NULL;

INSERT INTO stock_reservation(user_id,tier_id,event_id,session_id,request_key,order_no,status,
    quantity,unit_price,total_amount,expires_at,create_time,update_time,order_id,release_reason,attempts)
SELECT o.user_id,o.tier_id,o.event_id,o.session_id,CONCAT('legacy:',o.id),o.order_no,
    CASE WHEN o.order_status IN(1,3) THEN 'PAID' WHEN o.order_status=2 THEN 'RELEASED' ELSE 'ORDER_CREATED' END,
    o.quantity,ROUND(o.total_amount/GREATEST(o.quantity,1),2),o.total_amount,
    o.expire_time,o.create_time,COALESCE(o.update_time,o.create_time),o.id,
    CASE WHEN o.order_status=2 THEN 'LEGACY_CANCELED' ELSE NULL END,0
FROM order_info o;

-- Duplicate legacy purchases are preserved; application also checks active legacy orders.
INSERT INTO purchase_guard(user_id,tier_id,reservation_id,create_time,update_time)
SELECT user_id,tier_id,CASE WHEN COUNT(*)=1 THEN MAX(id) ELSE NULL END,MIN(create_time),NOW(6)
FROM stock_reservation WHERE status IN('RESERVED','ORDER_CREATED','PAID') GROUP BY user_id,tier_id;

UPDATE payment_compensation SET task_type='PAYMENT_QUERY',business_key=payment_no,
    payload=JSON_OBJECT('paymentNo',payment_no)
WHERE task_type IS NULL;

-- This pointer records a winning successful payment, never an unpaid activity.
-- Duplicate successful charges stay untouched for preflight review.
UPDATE order_info o JOIN (
    SELECT order_id,MAX(id) AS payment_id FROM payment_record
    WHERE pay_status=2 GROUP BY order_id HAVING COUNT(*)=1
) p ON p.order_id=o.id SET o.active_payment_id=p.payment_id;
