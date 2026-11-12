package com.campusoverflow.bootstrap.platform;

import com.campusoverflow.shared.command.CommandIdempotencyStore;
import com.campusoverflow.shared.util.UtcTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CommandIdempotencyStore} 的 JDBC 实现：以 {@code co_command_result} 的主键唯一性做原子占位。
 *
 * <p>并发说明：两个请求同时 begin 同一键时，后到的 INSERT 会等待前者的事务提交（InnoDB 主键锁），
 * 提交后必然能读到 SUCCEEDED 结果——不会出现重复执行。必须与业务更新处于同一事务
 * （{@link Propagation#MANDATORY} 强制），命令失败时占位行随事务回滚，可安全重试。
 */
@Component
public class JdbcCommandIdempotencyStore implements CommandIdempotencyStore {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcCommandIdempotencyStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BeginOutcome begin(String key, Duration ttl, Instant now) {
        BeginOutcome outcome = evaluate(key, ttl, now);
        if (outcome != null) {
            return outcome;
        }
        try {
            insertRunning(key, ttl, now);
            return BeginOutcome.EXECUTE;
        } catch (DuplicateKeyException e) {
            BeginOutcome raced = evaluate(key, ttl, now);
            return raced == null ? BeginOutcome.EXECUTE : raced;
        }
    }

    /** @return 键已存在时的判定；键不存在返回 null。 */
    private BeginOutcome evaluate(String key, Duration ttl, Instant now) {
        Row row = select(key);
        if (row == null) {
            return null;
        }
        if ("SUCCEEDED".equals(row.status)) {
            return BeginOutcome.ALREADY_SUCCEEDED;
        }
        if (row.expiresAt.isBefore(now)) {
            jdbc.update("""
                    UPDATE co_command_result SET status = 'RUNNING', result_json = NULL,
                        created_at = ?, expires_at = ?
                    WHERE idempotency_key = ?
                    """, UtcTime.toDb(now), UtcTime.toDb(now.plus(ttl)), key);
            return BeginOutcome.EXECUTE;
        }
        return BeginOutcome.IN_PROGRESS;
    }

    private void insertRunning(String key, Duration ttl, Instant now) {
        jdbc.update("""
                INSERT INTO co_command_result (idempotency_key, status, result_json, created_at, expires_at)
                VALUES (?, 'RUNNING', NULL, ?, ?)
                """, key, UtcTime.toDb(now), UtcTime.toDb(now.plus(ttl)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(String key, String resultJson, Instant now) {
        int updated = jdbc.update("""
                UPDATE co_command_result SET status = 'SUCCEEDED', result_json = ? WHERE idempotency_key = ?
                """, resultJson, key);
        if (updated == 0) {
            // 允许"业务事务中先完成副作用、占位行被回滚"的极端场景：直接落终态
            jdbc.update("""
                    INSERT INTO co_command_result (idempotency_key, status, result_json, created_at, expires_at)
                    VALUES (?, 'SUCCEEDED', ?, ?, ?)
                    """, key, resultJson, UtcTime.toDb(now),
                    UtcTime.toDb(now.plus(Duration.ofHours(24))));
        }
    }

    @Override
    public Optional<String> resultOf(String key) {
        try {
            Row row = select(key);
            return row != null && "SUCCEEDED".equals(row.status)
                    ? Optional.ofNullable(row.resultJson) : Optional.empty();
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private Row select(String key) {
        try {
            return jdbc.queryForObject(
                    "SELECT status, result_json, expires_at FROM co_command_result WHERE idempotency_key = ?",
                    (rs, i) -> new Row(rs.getString("status"), rs.getString("result_json"),
                            UtcTime.fromDb(rs.getObject("expires_at", LocalDateTime.class))),
                    key);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private record Row(String status, String resultJson, Instant expiresAt) {
    }
}
