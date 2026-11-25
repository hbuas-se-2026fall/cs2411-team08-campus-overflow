package com.campusoverflow.reputation.domain;

import java.time.Instant;

/** 课程预算的只追加流水：增减、来源、操作者、原因，全部留痕。 */
public record BudgetAdjustment(Long id, long courseId, int delta, BudgetAdjustmentSource source, long operatorId,
                               String reason, String eventId, Instant createdAt) {

    public static BudgetAdjustment of(long courseId, int delta, BudgetAdjustmentSource source, long operatorId,
                                      String reason, String eventId, Instant createdAt) {
        return new BudgetAdjustment(null, courseId, delta, source, operatorId, reason, eventId, createdAt);
    }

    public static BudgetAdjustment reconstitute(Long id, long courseId, int delta, BudgetAdjustmentSource source,
                                                long operatorId, String reason, String eventId, Instant createdAt) {
        return new BudgetAdjustment(id, courseId, delta, source, operatorId, reason, eventId, createdAt);
    }
}
