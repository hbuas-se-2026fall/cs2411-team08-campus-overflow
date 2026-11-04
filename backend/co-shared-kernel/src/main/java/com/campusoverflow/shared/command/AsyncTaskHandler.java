package com.campusoverflow.shared.command;

/**
 * 异步任务处理器（B01）：每种 task_type 注册一个实现，由轮询器分发。
 * 实现必须幂等——任务可能因进程崩溃在处理中途被重新认领。
 */
public interface AsyncTaskHandler {

    /** 本处理器负责的任务类型，对应 {@link AsyncTaskManager#schedule} 的 taskType。 */
    String taskType();

    /**
     * 执行任务。抛出任何异常都视为本次失败，由轮询器记录并安排重试。
     *
     * @param bizKey      业务对象键
     * @param payloadJson 入队时的上下文 JSON（可能为 null）
     */
    void handle(String bizKey, String payloadJson) throws Exception;
}
