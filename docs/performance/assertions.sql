-- Run on an isolated load-test database after quiescing new requests.
-- Every query below should return zero rows. Do not run cleanup against the original database.
SELECT id,total_stock,available_stock FROM ticket_tier
WHERE available_stock<0 OR available_stock>total_stock;

SELECT t.id,t.total_stock,t.available_stock,COALESCE(SUM(CASE WHEN r.status IN('RESERVED','ORDER_CREATED','PAID') THEN r.quantity ELSE 0 END),0) held
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
