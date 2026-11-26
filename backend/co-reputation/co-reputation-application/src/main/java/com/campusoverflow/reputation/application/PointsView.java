package com.campusoverflow.reputation.application;

/** 本人积分余额视图。pendingRecovery 为撤销后待追回金额（B06 正式处理）。 */
public record PointsView(long userId, int availablePoints, int frozenPoints, int totalPoints, int pendingRecovery) {
}
