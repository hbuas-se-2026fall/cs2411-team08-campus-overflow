package com.campusoverflow.shared.command;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 业务幂等键存储（B01）：为"扣款、生成、发布"等不可重复执行的命令提供精确一次语义。
 *
 * <p>推荐用法：命令开始前调用 {@link #begin}，业务副作用与状态标记在<b>同一事务</b>中提交；
 * 成功后调用 {@link #complete} 保存结果摘要。若命令在事务中失败，整个事务回滚（含占位行），
 * 下一次同键请求会重新执行——不会产生半成品状态。
 *
 * <p>三种 begin 结果：
 * <ul>
 *   <li>{@code EXECUTE} —— 键空闲或已过期，调用方执行业务；</li>
 *   <li>{@code ALREADY_SUCCEEDED} —— 直接把 {@code previousResult} 返回给客户端，不重放副作用；</li>
 *   <li>{@code IN_PROGRESS} —— 另一个请求正在处理（首请求尚未提交），返回 409 处理中，不猜结果。</li>
 * </ul>
 */
public interface CommandIdempotencyStore {

    /**
     * 占位或查询既有执行结果。
     *
     * @param key 业务幂等键（如 "bounty:post:question:42:user:7"）
     * @param ttl 结果保留时长，过期后允许重新执行
     * @param now 当前时间（UTC）
     */
    BeginOutcome begin(String key, Duration ttl, Instant now);

    /** 命令成功：保存结果摘要（通常为结果资源 ID 或状态 JSON），与业务副作用同事务提交。 */
    void complete(String key, String resultJson, Instant now);

    /** 查询既有结果；无记录或仅处理中时返回 empty。用于"命令超时后主动查询结果"场景。 */
    Optional<String> resultOf(String key);

    enum BeginOutcome {
        /** 执行业务。 */
        EXECUTE,
        /** 已成功过，返回 previousResult，不重放副作用。 */
        ALREADY_SUCCEEDED,
        /** 处理中，返回 409。 */
        IN_PROGRESS
    }
}
