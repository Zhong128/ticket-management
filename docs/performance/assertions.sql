-- Run on an isolated load-test database after quiescing new requests.
-- Exception queries should return zero rows; the final diagnostic query returns expected Redis quotas. Do not run cleanup against the original database.
SELECT id,total_stock,available_stock FROM ticket_tier
WHERE available_stock<0 OR available_stock>total_stock;

SELECT t.id,t.total_stock,t.available_stock,COALESCE(SUM(CASE WHEN r.inventory_state='HELD' THEN r.quantity ELSE 0 END),0) held
FROM ticket_tier t LEFT JOIN stock_reservation r ON r.tier_id=t.id
GROUP BY t.id,t.total_stock,t.available_stock
HAVING t.total_stock <> t.available_stock+held;

SELECT user_id,tier_id,COUNT(*) FROM stock_reservation
WHERE status IN('RESERVED','ORDER_CREATED','PAID') GROUP BY user_id,tier_id HAVING COUNT(*)>1;

SELECT order_no,COUNT(*) FROM order_info GROUP BY order_no HAVING COUNT(*)>1;
SELECT payment_id,COUNT(*) FROM refund_record GROUP BY payment_id HAVING COUNT(*)>1;
SELECT r.id FROM stock_reservation r LEFT JOIN order_info o ON o.id=r.order_id
WHERE r.status='ORDER_CREATED' AND (o.id IS NULL OR o.order_status NOT IN(0,4));
SELECT id FROM stock_reservation WHERE status IN('RESERVED','ORDER_CREATED') AND expires_at<DATE_SUB(NOW(),INTERVAL 2 MINUTE);
SELECT event_id FROM message_outbox WHERE publish_status<>'PUBLISHED' AND created_at<DATE_SUB(NOW(),INTERVAL 2 MINUTE);

-- 已占数据库库存的有效申请不能没有订单，待建单只能是PENDING。
SELECT id FROM stock_reservation
WHERE inventory_state='HELD' AND status='RESERVED' AND order_id IS NULL;

-- 仅为诊断输出：将expected_redis_quota与同代次Redis实际配额比较。
SELECT t.id,t.available_stock-COALESCE(SUM(CASE WHEN r.inventory_state='PENDING' THEN r.quantity ELSE 0 END),0) AS expected_redis_quota
FROM ticket_tier t LEFT JOIN stock_reservation r ON r.tier_id=t.id
GROUP BY t.id,t.available_stock;
