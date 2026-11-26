package com.campusoverflow.reputation.domain;

import java.util.Arrays;
import java.util.List;

/**
 * 账本流水的事由。除描述外还标记「币种」：声誉与积分共用一张只追加账本，
 * 由币种决定改哪个余额、是否发集成事件、是否计入 FF-8 对账。
 */
public enum ReputationReason {

    ANSWER_ACCEPTED("回答被采纳", Currency.REPUTATION),
    QUESTION_VOTE("问题被投票", Currency.REPUTATION),
    ANSWER_VOTE("回答被投票", Currency.REPUTATION),
    BOUNTY_AWARDED("赢得悬赏", Currency.REPUTATION),
    ANSWER_ENDORSED("教师认证", Currency.POINTS);

    /** 账本的两种货币，新增事由时只需指定币种，无需改动分支判断（OCP）。 */
    public enum Currency {
        REPUTATION, POINTS
    }

    private final String label;
    private final Currency currency;

    ReputationReason(String label, Currency currency) {
        this.label = label;
        this.currency = currency;
    }

    public String label() {
        return label;
    }

    public Currency currency() {
        return currency;
    }

    public boolean isVote() {
        return this == QUESTION_VOTE || this == ANSWER_VOTE;
    }

    public boolean isPoints() {
        return currency == Currency.POINTS;
    }

    /** 供持久化层按币种过滤流水（对账 / 积分流水分页）。 */
    public static List<String> namesOf(Currency currency) {
        return Arrays.stream(values()).filter(r -> r.currency == currency).map(Enum::name).toList();
    }
}
