-- B01 公共运行基础：业务幂等键 + 可靠异步任务。平台级表，不属于任何限界上下文。

-- 业务幂等键（CommandIdempotencyStore）：为扣款、生成、发布等命令提供精确一次语义。
-- 推荐与业务副作用同事务使用：命令失败时占位行随事务回滚，可安全重试。
CREATE TABLE co_command_result (
    idempotency_key VARCHAR(128)  NOT NULL COMMENT '业务幂等键，如 bounty:post:question:42:user:7',
    status          VARCHAR(16)   NOT NULL COMMENT 'RUNNING / SUCCEEDED',
    result_json     MEDIUMTEXT    NULL COMMENT '成功后的结果摘要（资源 ID 或状态 JSON）',
    created_at      DATETIME(6)   NOT NULL,
    expires_at      DATETIME(6)   NOT NULL COMMENT '过期后允许同键重新执行',
    PRIMARY KEY (idempotency_key),
    KEY idx_cmd_result_expiry (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 可靠异步任务（AsyncTaskManager）：业务后台任务状态落库，具备重试次数、下次执行时间与最后错误。
CREATE TABLE co_async_task (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    task_type    VARCHAR(64)  NOT NULL COMMENT '任务类型，对应 AsyncTaskHandler.taskType()',
    biz_key      VARCHAR(128) NOT NULL COMMENT '业务对象键，建议携带版本号（如 material:42:v3）',
    payload      MEDIUMTEXT   NULL COMMENT '处理器上下文 JSON',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RUNNING / SUCCEEDED / DEAD',
    attempts     INT          NOT NULL DEFAULT 0,
    max_attempts INT          NOT NULL DEFAULT 10,
    next_run_at  DATETIME(6)  NOT NULL,
    locked_until DATETIME(6)  NULL COMMENT '租约到期时间，防止多实例重复认领',
    locked_by    VARCHAR(40)  NULL,
    last_error   VARCHAR(500) NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_async_task (task_type, biz_key),
    KEY idx_async_task_pending (status, next_run_at, locked_until, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
