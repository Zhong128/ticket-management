-- Redis仅先预扣配额；申请可靠受理后，由消费者扣数据库库存。
ALTER TABLE stock_reservation
    ADD COLUMN inventory_state VARCHAR(16) NOT NULL DEFAULT 'HELD',
    ADD COLUMN redis_token VARCHAR(64) NULL,
    ADD COLUMN stock_epoch BIGINT NOT NULL DEFAULT 0;
UPDATE stock_reservation
SET inventory_state=CASE WHEN status='RELEASED' THEN 'RELEASED' ELSE 'HELD' END,
    redis_token=CONCAT('legacy:',id);
CREATE UNIQUE INDEX uk_reservation_redis_token ON stock_reservation(redis_token);
CREATE INDEX idx_reservation_inventory ON stock_reservation(tier_id,inventory_state,status);

-- 消息恢复任务未必已创建订单，不能伪造订单号或用户ID。
ALTER TABLE payment_compensation MODIFY COLUMN order_no VARCHAR(32) NULL,
    MODIFY COLUMN user_id BIGINT NULL;

CREATE TABLE seckill_admission (
    user_id BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    tier_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    redis_token VARCHAR(64) NOT NULL,
    stock_epoch BIGINT NOT NULL,
    admission_status VARCHAR(16) NOT NULL,
    reservation_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY(user_id,request_key),
    KEY idx_admission_token(redis_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE seckill_stock_epoch (
    tier_id BIGINT NOT NULL PRIMARY KEY,
    epoch BIGINT NOT NULL DEFAULT 0,
    cache_status VARCHAR(16) NOT NULL DEFAULT 'PAUSED',
    build_token VARCHAR(36) NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO seckill_admission(user_id,request_key,tier_id,quantity,redis_token,stock_epoch,
    admission_status,reservation_id,created_at,updated_at)
SELECT user_id,request_key,tier_id,quantity,redis_token,stock_epoch,'ACCEPTED',id,create_time,update_time
FROM stock_reservation;
