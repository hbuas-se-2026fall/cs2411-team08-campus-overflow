package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.security.Role;

/**
 * 声誉规则手册——把“游戏规则”集中在一处，便于课堂讨论与参数调整。
 * <ul>
 *   <li>回答被采纳 +15（自问自答被采纳不加分）；</li>
 *   <li>被赞 +10，被踩 −2；改投、撤销按“新效果 − 旧效果”计算增量；</li>
 *   <li>反作弊：每人每个自然日（UTC）因被投票获得的正向声誉最多 200；</li>
 *   <li>教师认证 +5 积分（与声誉分账）；</li>
 *   <li>对外展示的声誉下限为 0，账本保留真实负值。</li>
 * </ul>
 */
public final class ReputationRulebook {

    public static final int ACCEPTED = 15;
    public static final int UPVOTE = 10;
    public static final int DOWNVOTE = -2;
    public static final int DAILY_VOTE_CAP = 200;
    public static final int ENDORSEMENT_POINTS = 5;

    private ReputationRulebook() {
    }

    public static int voteEffect(int value) {
        return switch (value) {
            case 1 -> UPVOTE;
            case -1 -> DOWNVOTE;
            case 0 -> 0;
            default -> throw new IllegalArgumentException("投票值只能是 -1、0 或 1：" + value);
        };
    }

    public static int voteDelta(int oldValue, int newValue) {
        return voteEffect(newValue) - voteEffect(oldValue);
    }

    public static int acceptedDelta(long answerAuthorId, long questionAuthorId) {
        return answerAuthorId == questionAuthorId ? 0 : ACCEPTED;
    }

    /** 对正向投票增量应用每日上限；负向增量不受限。 */
    public static int applyDailyCap(int delta, int positiveVoteGainToday) {
        if (delta <= 0) {
            return delta;
        }
        int remaining = DAILY_VOTE_CAP - positiveVoteGainToday;
        return remaining <= 0 ? 0 : Math.min(delta, remaining);
    }

    /** 开户时发放的悬赏积分（与声誉是两种不同的“货币”）。 */
    public static int initialPoints(Role role) {
        return switch (role) {
            case TEACHER, ADMIN -> 1000;
            case STUDENT, TA -> 0;
        };
    }

    /** 冲正：只追加一条相反流水，不修改或删除原流水。 */
    public static int reversalDelta(int originalDelta) {
        return -originalDelta;
    }

    /** 展示下限：负声誉对外显示 0，账本仍保留真实负值（防套利由展示层处理）。 */
    public static int displayReputation(int reputation) {
        return Math.max(0, reputation);
    }
}
