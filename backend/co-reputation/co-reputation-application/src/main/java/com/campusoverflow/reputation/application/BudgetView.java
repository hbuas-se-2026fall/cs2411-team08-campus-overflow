package com.campusoverflow.reputation.application;

import java.util.List;

/** 课程积分预算视图。available + frozen 守恒，recentAdjustments 为最近 20 条只追加流水。 */
public record BudgetView(long courseId, String courseName, int availablePoints, int frozenPoints, int totalPoints,
                         List<BudgetAdjustmentView> recentAdjustments) {
}
