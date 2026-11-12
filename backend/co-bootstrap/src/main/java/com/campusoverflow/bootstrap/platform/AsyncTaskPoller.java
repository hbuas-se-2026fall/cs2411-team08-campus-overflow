package com.campusoverflow.bootstrap.platform;

import com.campusoverflow.shared.command.AsyncTaskHandler;
import com.campusoverflow.shared.command.AsyncTaskManager;
import com.campusoverflow.shared.util.UtcTime;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 异步任务轮询器（B01）：认领到期任务 → 交给对应 {@link AsyncTaskHandler} 执行 → 记录成败。
 * <ul>
 *   <li>认领：{@code FOR UPDATE SKIP LOCKED} + 租约（locked_until），多实例并行不重复认领
 *       （模式与 {@code OutboxRelay} 一致）；</li>
 *   <li>至少一次：进程崩溃后租约到期，任务被重新认领，处理器必须幂等；</li>
 *   <li>没有注册处理器的任务类型直接转 DEAD，避免无限重试空转；</li>
 *   <li>处理器串行执行：单个 handler 内部如需并发，自行拆分更细粒度的任务。</li>
 * </ul>
 */
@Component
public class AsyncTaskPoller {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskPoller.class);
    private static final Duration LEASE = Duration.ofSeconds(60);

    private final JdbcTemplate jdbc;
    private final AsyncTaskManager manager;
    private final Clock clock;
    private final Map<String, AsyncTaskHandler> handlers;
    private final int batchSize;
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    private record Task(String taskType, String bizKey, String payload) {
    }

    public AsyncTaskPoller(JdbcTemplate jdbc, AsyncTaskManager manager, Clock clock,
                           ObjectProvider<AsyncTaskHandler> handlerProvider,
                           @Value("${co.task.batch-size:10}") int batchSize) {
        this.jdbc = jdbc;
        this.manager = manager;
        this.clock = clock;
        this.handlers = handlerProvider.orderedStream()
                .collect(Collectors.toMap(AsyncTaskHandler::taskType, Function.identity()));
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${co.task.poll-interval-ms:2000}")
    public void poll() {
        for (Task task : claim()) {
            dispatch(task);
        }
    }

    private List<Task> claim() {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM co_async_task
                WHERE status = 'PENDING' AND next_run_at <= ? AND (locked_until IS NULL OR locked_until < ?)
                ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED
                """, Long.class, UtcTime.toDb(clock.instant()), UtcTime.toDb(clock.instant()), batchSize);
        if (ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().map(id -> {
            try {
                return jdbc.queryForObject("""
                        SELECT task_type, biz_key, payload FROM co_async_task WHERE id = ?
                        """, (rs, i) -> new Task(rs.getString("task_type"), rs.getString("biz_key"),
                        rs.getString("payload")), id);
            } catch (EmptyResultDataAccessException e) {
                return null;
            }
        }).toList();
    }

    private void dispatch(Task task) {
        AsyncTaskHandler handler = handlers.get(task.taskType());
        if (handler == null) {
            manager.fail(task.taskType(), task.bizKey(), "没有注册的处理器: " + task.taskType(), clock.instant());
            return;
        }
        jdbc.update("UPDATE co_async_task SET status = 'RUNNING', locked_until = ?, locked_by = ? WHERE task_type = ? AND biz_key = ?",
                UtcTime.toDb(clock.instant().plus(LEASE)), instanceId, task.taskType(), task.bizKey());
        try {
            handler.handle(task.bizKey(), task.payload);
            manager.succeed(task.taskType(), task.bizKey(), clock.instant());
        } catch (Exception e) {
            log.warn("异步任务失败 type={} key={}: {}", task.taskType(), task.bizKey(), e.getMessage());
            manager.fail(task.taskType(), task.bizKey(), String.valueOf(e.getMessage()), clock.instant());
        }
    }
}
