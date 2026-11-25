package com.campusoverflow.reputation.application;

import java.time.Instant;

/** 待追回记录的复核视图。currentGap 是实时账户缺口，供管理员判断是否已自动回升。 */
public record PointsRecoveryView(long id, long userId, int amount, String status, String sourceEventId,
                                 Instant createdAt, Instant resolvedAt, Long resolvedBy, String note,
                                 int currentGap) {
}
