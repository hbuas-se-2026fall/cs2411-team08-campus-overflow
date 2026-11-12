package com.campusoverflow.bootstrap.platform;

import com.campusoverflow.shared.command.AsyncTaskManager;
import com.campusoverflow.shared.util.UtcTime;
import java.time.Clock;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AsyncTaskManager} 的 JDBC 实现：以 {@code (task_type, biz_key)} 唯一键做原子入队。
 */
@Component
public class JdbcAsyncTaskManager implements AsyncTaskManager {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcAsyncTaskManager(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean schedule(String taskType, String bizKey, String payload, Instant runAt, Instant now) {
        try {
            jdbc.update("""
                    INSERT INTO co_async_task (task_type, biz_key, payload, status, attempts, max_attempts,
                        next_run_at, created_at, updated_at)
                    VALUES (?, ?, ?, 'PENDING', 0, 10, ?, ?, ?)
                    """, taskType, bizKey, payload, UtcTime.toDb(runAt),
                    UtcTime.toDb(now), UtcTime.toDb(now));
            return true;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return false; // 同键任务已存在：保持既有状态，不覆盖
        }
    }

    @Override
    public void succeed(String taskType, String bizKey, Instant now) {
        jdbc.update("""
                UPDATE co_async_task SET status = 'SUCCEEDED', last_error = NULL, locked_until = NULL,
                    locked_by = NULL, updated_at = ?
                WHERE task_type = ? AND biz_key = ?
                """, UtcTime.toDb(now), taskType, bizKey);
    }

    @Override
    public void fail(String taskType, String bizKey, String error, Instant now) {
        int attempts = jdbc.queryForObject("""
                SELECT attempts FROM co_async_task WHERE task_type = ? AND biz_key = ?
                """, Integer.class, taskType, bizKey);
        int next = attempts + 1;
        String message = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        if (next >= 10) { // max_attempts 默认 10；超限转死信等待人工处理
            jdbc.update("""
                    UPDATE co_async_task SET status = 'DEAD', attempts = ?, last_error = ?,
                        locked_until = NULL, locked_by = NULL, updated_at = ?
                    WHERE task_type = ? AND biz_key = ?
                    """, next, message, UtcTime.toDb(now), taskType, bizKey);
        } else {
            // 指数退避：2^n 秒，封顶 300 秒
            Instant retryAt = now.plusSeconds(Math.min(1L << Math.min(next, 8), 300));
            jdbc.update("""
                    UPDATE co_async_task SET status = 'PENDING', attempts = ?, last_error = ?, next_run_at = ?,
                        locked_until = NULL, locked_by = NULL, updated_at = ?
                    WHERE task_type = ? AND biz_key = ?
                    """, next, message, UtcTime.toDb(retryAt), UtcTime.toDb(now), taskType, bizKey);
        }
    }
}
