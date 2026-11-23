package com.campusoverflow.reputation.domain;

import java.util.EnumSet;
import java.util.Set;

/** 徽章授予规则（纯函数，易于测试与扩展）。 */
public final class BadgePolicy {

    private BadgePolicy() {
    }

    public static Set<BadgeType> forAcceptedCount(long acceptedCount) {
        Set<BadgeType> result = EnumSet.noneOf(BadgeType.class);
        if (acceptedCount >= 1) {
            result.add(BadgeType.FIRST_ACCEPTED);
        }
        if (acceptedCount >= 10) {
            result.add(BadgeType.HELPER_10);
        }
        return result;
    }

    public static Set<BadgeType> forReputation(int reputation) {
        Set<BadgeType> result = EnumSet.noneOf(BadgeType.class);
        if (reputation >= 100) {
            result.add(BadgeType.REPUTATION_100);
        }
        if (reputation >= 1000) {
            result.add(BadgeType.REPUTATION_1000);
        }
        return result;
    }
}
