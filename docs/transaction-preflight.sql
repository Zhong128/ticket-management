-- Read-only checks. Run on a database copy before opting into the new trade path.
SELECT user_id,tier_id,COUNT(*) AS active_count FROM order_info
WHERE order_status IN(0,1,3,4) GROUP BY user_id,tier_id HAVING COUNT(*)>1;
SELECT order_id,COUNT(*) AS active_payments FROM payment_record
WHERE pay_status IN(0,1) GROUP BY order_id HAVING COUNT(*)>1;
SELECT id,total_stock,available_stock FROM ticket_tier
WHERE available_stock<0 OR available_stock>total_stock;
SELECT id,order_no,order_status,quantity,total_amount FROM order_info
WHERE quantity<1 OR total_amount<0 OR create_time IS NULL OR order_status NOT IN(0,1,2,3,4);
SELECT p.id,p.payment_no,p.pay_status,o.order_status FROM payment_record p
JOIN order_info o ON o.id=p.order_id WHERE p.pay_status=2 AND o.order_status NOT IN(1,3);
