package com.campusoverflow.reputation.application;

/** 撤销后「积分已花费」的待追回记录入口。函数式接口便于既有测试用 no-op 实现。 */
@FunctionalInterface
public interface PointsRecoveryRecorder {

    void ensurePending(long userId, int amount, String sourceEventId);
}
