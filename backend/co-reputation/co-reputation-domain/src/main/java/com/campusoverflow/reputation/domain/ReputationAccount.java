package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;

/**
 * 声誉账户。不变量：reputation 恒等于该用户所有 REPUTATION 币种账本流水 delta 之和（由对账任务 FF-8 验证）。
 * 悬赏积分 = 可用 + 冻结，冻结部分在悬赏结算或到期时释放；积分撤销后允许为负（待追回），
 * 此时 freezePoints 会自然拒绝新的支出。
 */
public class ReputationAccount {

    private final long userId;
    private int reputation;
    private int availablePoints;
    private int frozenPoints;
    private final long version;

    private ReputationAccount(long userId, int reputation, int availablePoints, int frozenPoints, long version) {
        this.userId = userId;
        this.reputation = reputation;
        this.availablePoints = availablePoints;
        this.frozenPoints = frozenPoints;
        this.version = version;
    }

    public static ReputationAccount open(long userId, int initialPoints) {
        return new ReputationAccount(userId, 0, initialPoints, 0, 0);
    }

    public static ReputationAccount reconstitute(long userId, int reputation, int availablePoints, int frozenPoints,
                                                 long version) {
        return new ReputationAccount(userId, reputation, availablePoints, frozenPoints, version);
    }

    /** 只允许通过账本流水改变声誉（由应用服务保证先写流水）。 */
    public void applyReputation(int delta) {
        reputation += delta;
    }

    /** 只允许通过账本流水改变积分；允许扣成负数，代表撤销后待追回的金额。 */
    public void applyPoints(int delta) {
        availablePoints += delta;
    }

    /** 对外展示的声誉下限为 0，账本保留真实负值。 */
    public int displayReputation() {
        return ReputationRulebook.displayReputation(reputation);
    }

    /** 撤销后积分被扣成负数时，待追回的金额；余额充足时为 0。 */
    public int pendingRecovery() {
        return Math.max(0, -availablePoints);
    }

    public void freezePoints(int points) {
        requirePositive(points);
        if (points > availablePoints) {
            throw new BusinessRuleException("REP-1002", "可用积分不足");
        }
        availablePoints -= points;
        frozenPoints += points;
    }

    /** 悬赏成功发放：冻结积分转出给获奖者（以声誉形式），发起人账户中消失。 */
    public void spendFrozen(int points) {
        requirePositive(points);
        ensureFrozen(points);
        frozenPoints -= points;
    }

    /** 悬赏到期：冻结积分退回可用。 */
    public void refundFrozen(int points) {
        requirePositive(points);
        ensureFrozen(points);
        frozenPoints -= points;
        availablePoints += points;
    }

    private void ensureFrozen(int points) {
        if (points > frozenPoints) {
            throw new BusinessRuleException("REP-1003", "冻结积分不足");
        }
    }

    private static void requirePositive(int points) {
        if (points <= 0) {
            throw new BusinessRuleException("REP-1004", "积分必须为正整数");
        }
    }

    public long userId() { return userId; }
    public int reputation() { return reputation; }
    public int availablePoints() { return availablePoints; }
    public int frozenPoints() { return frozenPoints; }
    public long version() { return version; }
}
