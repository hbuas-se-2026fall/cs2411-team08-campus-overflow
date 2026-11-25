package com.campusoverflow.reputation.domain;

/**
 * 可冻结积分的钱包。悬赏资金可以来自个人账户或课程预算，
 * 两者共用同一套「可用 + 冻结」守恒语义，BountyService 因此不区分来源（DIP / OCP）。
 */
public interface PointsWallet {

    void freezePoints(int points);

    void spendFrozen(int points);

    void refundFrozen(int points);

    int availablePoints();

    int frozenPoints();
}
