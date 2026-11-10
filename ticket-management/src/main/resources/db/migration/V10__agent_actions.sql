-- 仅新增客服确认草稿；不修改业务交易表，不自动执行交易。
CREATE TABLE agent_action (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    title VARCHAR(100) NULL,
    event_name VARCHAR(255) NULL,
    session_name VARCHAR(255) NULL,
    session_time DATETIME(6) NULL,
    tier_name VARCHAR(100) NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_status VARCHAR(32) NULL,
    order_id BIGINT NULL,
    message VARCHAR(200) NULL,
    content_erased TINYINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_action_key(user_id, request_key),
    KEY idx_agent_action_conversation(conversation_id, content_erased, created_at),
    KEY idx_agent_action_run(run_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 同用户同操作目标跨会话串行化，不让未知结果通过新卡片换键重试。
CREATE TABLE agent_action_guard (
    user_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    action_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    PRIMARY KEY(user_id, type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
