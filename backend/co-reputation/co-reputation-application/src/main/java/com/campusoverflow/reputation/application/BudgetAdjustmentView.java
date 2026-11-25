package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.domain.BudgetAdjustment;
import java.time.Instant;

public record BudgetAdjustmentView(long id, int delta, String source, long operatorId, String reason,
                                   String eventId, Instant createdAt) {

    public static BudgetAdjustmentView from(BudgetAdjustment a) {
        return new BudgetAdjustmentView(a.id() == null ? 0 : a.id(), a.delta(), a.source().name(), a.operatorId(),
                a.reason(), a.eventId(), a.createdAt());
    }
}
