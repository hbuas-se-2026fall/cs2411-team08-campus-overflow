package com.campusoverflow.shared.command;

import java.time.Instant;

/**
 * 可靠异步任务管理（B01）：业务后台任务（悬赏到期、资料处理、超时提醒等）的状态必须落库，
 * 具备状态、重试次数、下次执行时间和最后错误，不依赖进程内存（B01 验收条款）。
 *
 * <p>与 outbox 的分工：outbox 负责"业务事务提交后投递集成事件"；本设施负责"按计划执行一段业务工作"。
 * 任务以 {@code (taskType, bizKey)} 唯一——同键重复入队只会保留一个，bizKey 建议携带版本号
 * （如 {@code material:42:v3}）以支持"新版本重新处理"。
 *
 * <p>至少一次执行语义：处理失败按指数退避重试，超过上限转 DEAD 并暴露指标；处理器自身须幂等。
 */
public interface AsyncTaskManager {

    /**
     * 入队一个任务。若 {@code (taskType, bizKey)} 已存在（任意状态），不做任何修改并返回 false——
     * 由调用方用携带版本号的 bizKey 表达"重新处理"。
     *
     * @param taskType 任务类型标识（如 MATERIAL_PROCESS、BOUNTY_EXPIRE）
     * @param bizKey   业务对象键，同类型内唯一
     * @param payload  处理器需要的上下文 JSON（可为 null）
     * @param runAt    期望执行时间；立即执行传当前时间
     */
    boolean schedule(String taskType, String bizKey, String payload, Instant runAt, Instant now);

    /** 标记成功。由轮询器在处理器正常返回后调用。 */
    void succeed(String taskType, String bizKey, Instant now);

    /**
     * 标记失败：按指数退避安排下次执行；超过最大重试次数转 DEAD（死信，等待人工处理）。
     */
    void fail(String taskType, String bizKey, String error, Instant now);
}
