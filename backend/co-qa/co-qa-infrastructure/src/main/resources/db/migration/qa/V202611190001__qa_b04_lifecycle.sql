-- B04-2：问题状态机、回答来源、采纳复核留痕、认证历史。
-- 状态与软删除双轨：查询一律以 deleted_at 为准，status 供治理与前端展示。

ALTER TABLE qa_question
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESOLVED/CLOSED/DELETED';

UPDATE qa_question
SET status = CASE
    WHEN deleted_at IS NOT NULL THEN 'DELETED'
    WHEN accepted_answer_id IS NOT NULL THEN 'RESOLVED'
    ELSE 'OPEN'
END;

ALTER TABLE qa_answer
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'HUMAN' COMMENT 'HUMAN/AI/AI_ASSISTED';

CREATE TABLE qa_acceptance_review (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    question_id    BIGINT       NOT NULL,
    reviewer_id    BIGINT       NOT NULL,
    question_version BIGINT     NOT NULL COMMENT '复核时的问题版本（乐观锁快照）',
    result         VARCHAR(16)  NOT NULL COMMENT 'APPROVED',
    reason         VARCHAR(500) NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_review_question (question_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE qa_endorsement_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    answer_id      BIGINT       NOT NULL,
    endorser_id    BIGINT       NOT NULL,
    answer_version BIGINT       NOT NULL COMMENT '认证/撤销时的答案版本，供声誉幂等',
    action         VARCHAR(16)  NOT NULL COMMENT 'GRANT/REVOKE',
    reason         VARCHAR(500) NULL COMMENT 'REVOKE 原因；EDITED 表示答案编辑导致认证失效',
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_endorsement_log_answer (answer_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
