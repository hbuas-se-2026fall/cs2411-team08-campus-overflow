package com.campusoverflow.reputation.domain;

public enum PointsRecoveryStatus {
    /** 撤销后积分已花费，缺口待追回。 */
    PENDING,
    /** 余额回升自动冲销，或管理员确认已追回。 */
    RESOLVED,
    /** 管理员复核后豁免。 */
    WAIVED
}
