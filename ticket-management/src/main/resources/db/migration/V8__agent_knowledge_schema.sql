-- 仅新增站内规则表与来源元数据；不更新交易数据。回退应用时保留结构与历史来源。
CREATE TABLE agent_knowledge_document (
    document_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version INT NOT NULL,
    title VARCHAR(160) NOT NULL,
    reviewed_at DATE NOT NULL,
    source_path VARCHAR(240) NOT NULL,
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY(document_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE agent_knowledge_chunk (
    entry_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    document_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    document_version INT NOT NULL,
    question VARCHAR(240) NOT NULL,
    aliases VARCHAR(1000) NOT NULL,
    answer TEXT NOT NULL,
    live_check TEXT NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY(entry_id, document_version),
    KEY idx_agent_knowledge_document(document_id, document_version, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE agent_run ADD COLUMN citation_data TEXT NULL;
ALTER TABLE agent_message ADD COLUMN citation_data TEXT NULL;
