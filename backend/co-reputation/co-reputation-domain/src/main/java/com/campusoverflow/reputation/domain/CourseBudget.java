package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;

/**
 * 课程积分预算。与个人账户同构：可用 + 冻结守恒，冻结部分只能由悬赏终态释放。
 * 预算的增减一律记入 rep_budget_adjustment，不写 rep_ledger（来源分账不混）。
 */
public class CourseBudget implements PointsWallet {

    private final long courseId;
    private int availablePoints;
    private int frozenPoints;
    private final long version;

    private CourseBudget(long courseId, int availablePoints, int frozenPoints, long version) {
        this.courseId = courseId;
        this.availablePoints = availablePoints;
        this.frozenPoints = frozenPoints;
        this.version = version;
    }

    public static CourseBudget open(long courseId) {
        return new CourseBudget(courseId, 0, 0, 0);
    }

    public static CourseBudget reconstitute(long courseId, int availablePoints, int frozenPoints, long version) {
        return new CourseBudget(courseId, availablePoints, frozenPoints, version);
    }

    /** 管理员分配/回收：只作用于可用部分，不允许把可用预算扣成负数。 */
    public void adjust(int delta) {
        if (delta == 0) {
            throw new BusinessRuleException("REP-5004", "调整金额必须非零");
        }
        if (availablePoints + delta < 0) {
            throw new BusinessRuleException("REP-5001", "课程预算不足，无法回收该金额");
        }
        availablePoints += delta;
    }

    @Override
    public void freezePoints(int points) {
        requirePositive(points);
        if (points > availablePoints) {
            throw new BusinessRuleException("REP-5001", "课程预算可用积分不足");
        }
        availablePoints -= points;
        frozenPoints += points;
    }

    @Override
    public void spendFrozen(int points) {
        requirePositive(points);
        if (points > frozenPoints) {
            throw new BusinessRuleException("REP-5003", "课程预算冻结积分不足");
        }
        frozenPoints -= points;
    }

    @Override
    public void refundFrozen(int points) {
        requirePositive(points);
        if (points > frozenPoints) {
            throw new BusinessRuleException("REP-5003", "课程预算冻结积分不足");
        }
        frozenPoints -= points;
        availablePoints += points;
    }

    private static void requirePositive(int points) {
        if (points <= 0) {
            throw new BusinessRuleException("REP-5004", "积分必须为正整数");
        }
    }

    public long courseId() { return courseId; }
    @Override public int availablePoints() { return availablePoints; }
    @Override public int frozenPoints() { return frozenPoints; }
    public long version() { return version; }
}
