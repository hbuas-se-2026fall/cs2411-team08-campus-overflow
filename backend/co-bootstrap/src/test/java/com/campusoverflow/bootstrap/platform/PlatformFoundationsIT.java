package com.campusoverflow.bootstrap.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.shared.command.AsyncTaskManager;
import com.campusoverflow.shared.command.CommandIdempotencyStore;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * B01 平台基础设施集成测试（真实 MySQL，验证 Flyway 迁移与存储语义）：
 * 幂等键的精确一次语义、异步任务的入队去重与失败退避。
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "co.task.poll-interval-ms=60000" // 不让轮询器抢任务，本测试直接调 manager
})
class PlatformFoundationsIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.39"))
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    @Autowired
    CommandIdempotencyStore idempotency;

    @Autowired
    AsyncTaskManager tasks;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant now = Instant.parse("2026-11-04T08:00:00Z");

    @Test
    void 幂等键首次执行_成功后同键不再重放() {
        String key = "test:cmd:1";
        int[] executions = {0};

        String first = tx.execute(status -> {
            if (idempotency.begin(key, Duration.ofHours(24), now) != CommandIdempotencyStore.BeginOutcome.EXECUTE) {
                return "unexpected";
            }
            executions[0]++;
            idempotency.complete(key, "{\"id\":42}", now);
            return "done";
        });

        assertThat(first).isEqualTo("done");
        String replay = tx.execute(status -> {
            if (idempotency.begin(key, Duration.ofHours(24), now)
                    != CommandIdempotencyStore.BeginOutcome.ALREADY_SUCCEEDED) {
                return "re-executed";
            }
            return idempotency.resultOf(key).orElse("no-result");
        });

        assertThat(replay).isEqualTo("{\"id\":42}");
        assertThat(executions[0]).isEqualTo(1);
    }

    @Test
    void 幂等键失败回滚后可重新执行() {
        String key = "test:cmd:rollback";
        try {
            tx.executeWithoutResult(status -> {
                idempotency.begin(key, Duration.ofHours(24), now);
                throw new IllegalStateException("业务失败");
            });
        } catch (IllegalStateException expected) {
            // 占位行随事务回滚
        }

        Boolean reExecuted = tx.execute(status ->
                idempotency.begin(key, Duration.ofHours(24), now)
                        == CommandIdempotencyStore.BeginOutcome.EXECUTE);

        assertThat(reExecuted).isTrue();
    }

    @Test
    void 异步任务同键去重且失败后退避() {
        boolean first = tasks.schedule("TEST_TASK", "biz:1", "{\"n\":1}", now, now);
        boolean duplicate = tasks.schedule("TEST_TASK", "biz:1", "{\"n\":2}", now, now);

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();

        tasks.fail("TEST_TASK", "biz:1", "boom", now);
        assertThat(queryInt("SELECT attempts FROM co_async_task WHERE task_type='TEST_TASK' AND biz_key='biz:1'"))
                .isEqualTo(1);
        assertThat(queryString("SELECT status FROM co_async_task WHERE task_type='TEST_TASK' AND biz_key='biz:1'"))
                .isEqualTo("PENDING");
        // 2^1 = 2 秒退避（UTC）
        assertThat(queryString("SELECT DATE_FORMAT(next_run_at, '%Y-%m-%dT%H:%i:%sZ') FROM co_async_task "
                + "WHERE task_type='TEST_TASK' AND biz_key='biz:1'"))
                .isEqualTo("2026-11-04T08:00:02Z");

        // 重试耗尽转死信
        for (int i = 0; i < 9; i++) {
            tasks.fail("TEST_TASK", "biz:1", "boom again", now);
        }
        assertThat(queryString("SELECT status FROM co_async_task WHERE task_type='TEST_TASK' AND biz_key='biz:1'"))
                .isEqualTo("DEAD");
    }

    @Test
    void 处理成功记录终态() {
        tasks.schedule("TEST_TASK", "biz:ok", null, now, now);
        tasks.succeed("TEST_TASK", "biz:ok", now);

        assertThat(queryString("SELECT status FROM co_async_task WHERE task_type='TEST_TASK' AND biz_key='biz:ok'"))
                .isEqualTo("SUCCEEDED");
    }

    private Integer queryInt(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private String queryString(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }
}
