-- B06：悬赏资金来源、幂等与终态约束；课程预算与待追回三张新表
ALTER TABLE rep_bounty
    ADD COLUMN source_type VARCHAR(16)  NOT NULL DEFAULT 'PERSONAL' COMMENT 'PERSONAL / COURSE_BUDGET',
    ADD COLUMN source_id   BIGINT       NOT NULL DEFAULT 0 COMMENT 'PERSONAL=userId，COURSE_BUDGET=courseId',
    ADD COLUMN command_key VARCHAR(128) NULL COMMENT '客户端幂等键，NULL 表示未启用',
    ADD COLUMN disputed    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '获奖后判错，待复核',
    ADD COLUMN open_slot   BIGINT       NULL COMMENT 'OPEN 时等于 question_id，终态为 NULL；唯一索引保证一问题最多一个开放悬赏',
    ADD UNIQUE KEY uk_bounty_open_question (open_slot),
    ADD UNIQUE KEY uk_bounty_command (command_key),
    ADD KEY idx_bounty_source (source_type, source_id);

-- 历史 OPEN 悬赏补上 open_slot，使其同样受唯一约束保护
UPDATE rep_bounty SET source_id = sponsor_id, open_slot = question_id WHERE status = 'OPEN';

CREATE TABLE rep_course_budget (
    course_id        BIGINT NOT NULL,
    available_points INT    NOT NULL DEFAULT 0,
    frozen_points    INT    NOT NULL DEFAULT 0,
    version          BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE rep_budget_adjustment (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    course_id   BIGINT       NOT NULL,
    delta       INT          NOT NULL,
    source      VARCHAR(24)  NOT NULL COMMENT 'ADMIN_ALLOCATION / BOUNTY_SPEND / BOUNTY_REFUND',
    operator_id BIGINT       NOT NULL,
    reason      VARCHAR(255) NOT NULL,
    event_id    VARCHAR(128) NOT NULL COMMENT '幂等键，同一事件只记一笔',
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_budget_event (event_id, course_id),
    KEY idx_budget_course (course_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE rep_points_recovery (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    amount          INT         NOT NULL COMMENT '待追回缺口',
    status          VARCHAR(16) NOT NULL COMMENT 'PENDING / RESOLVED / WAIVED',
    source_event_id VARCHAR(64) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    resolved_at     DATETIME(6) NULL,
    resolved_by     BIGINT      NULL,
    note            VARCHAR(255) NULL,
    pending_slot    BIGINT      NULL COMMENT 'PENDING 时等于 user_id，终态为 NULL；唯一索引保证一人一条待追回',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recovery_pending (pending_slot),
    KEY idx_recovery_status (status, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
