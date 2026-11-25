package com.campusoverflow.reputation.domain;

/** 课程预算流水的来源。 */
public enum BudgetAdjustmentSource {
    /** 管理员分配 / 回收。 */
    ADMIN_ALLOCATION,
    /** 悬赏结算时从冻结预算中转出。 */
    BOUNTY_SPEND,
    /** 悬赏到期、问题删除或自赢时冻结预算退回。 */
    BOUNTY_REFUND
}
