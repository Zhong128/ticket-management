ALTER TABLE ticket_tier ADD COLUMN stock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE order_info ADD COLUMN expire_time DATETIME(6) NULL,
    ADD COLUMN active_payment_id BIGINT NULL,
    ADD COLUMN cancel_reason VARCHAR(255) NULL,
    ADD COLUMN remark VARCHAR(500) NULL;
CREATE INDEX idx_order_status_expiry ON order_info(order_status,expire_time);
CREATE INDEX idx_order_user_tier_status ON order_info(user_id,tier_id,order_status);

CREATE TABLE stock_reservation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL, tier_id BIGINT NOT NULL, event_id BIGINT NOT NULL, session_id BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL, order_no VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL, quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL, total_amount DECIMAL(10,2) NOT NULL,
    expires_at DATETIME(6) NOT NULL, create_time DATETIME(6) NOT NULL, update_time DATETIME(6) NOT NULL,
    order_id BIGINT NULL, release_reason VARCHAR(255) NULL, attempts INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_reservation_request(user_id,request_key), UNIQUE KEY uk_reservation_order(order_no),
    KEY idx_reservation_expiry(status,expires_at), KEY idx_reservation_order_id(order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_guard (
    user_id BIGINT NOT NULL, tier_id BIGINT NOT NULL, reservation_id BIGINT NULL,
    create_time DATETIME(6) NOT NULL, update_time DATETIME(6) NOT NULL,
    PRIMARY KEY(user_id,tier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE message_outbox (
    event_id VARCHAR(36) NOT NULL PRIMARY KEY, business_key VARCHAR(160) NOT NULL,
    event_type VARCHAR(32) NOT NULL, payload JSON NOT NULL,
    publish_status VARCHAR(16) NOT NULL DEFAULT 'NEW', next_attempt_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0, lease_token VARCHAR(36) NULL, lease_until DATETIME(6) NULL,
    last_error VARCHAR(500) NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_outbox_business(business_key), KEY idx_outbox_due(publish_status,next_attempt_at),
    KEY idx_outbox_lease(lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refund_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, refund_no VARCHAR(64) NOT NULL,
    payment_id BIGINT NOT NULL, payment_no VARCHAR(64) NOT NULL, order_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL, reason VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0, next_attempt_at DATETIME(6) NOT NULL,
    lease_token VARCHAR(36) NULL, lease_until DATETIME(6) NULL, last_error VARCHAR(500) NULL,
    trade_no VARCHAR(64) NULL, response_data TEXT NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_refund_no(refund_no), UNIQUE KEY uk_refund_payment(payment_id),
    KEY idx_refund_due(status,next_attempt_at), KEY idx_refund_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE payment_compensation MODIFY COLUMN payment_no VARCHAR(64) NULL,
    MODIFY COLUMN order_id BIGINT NULL,
    ADD COLUMN task_type VARCHAR(32) NULL,
    ADD COLUMN business_key VARCHAR(160) NULL,
    ADD COLUMN payload JSON NULL,
    ADD COLUMN lease_token VARCHAR(36) NULL,
    ADD COLUMN lease_until DATETIME(6) NULL;
ALTER TABLE payment_compensation DROP INDEX uk_payment_no;
CREATE UNIQUE INDEX uk_compensation_business ON payment_compensation(task_type,business_key);
CREATE INDEX idx_compensation_lease ON payment_compensation(task_status,lease_until);
