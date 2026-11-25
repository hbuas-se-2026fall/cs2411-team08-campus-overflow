package com.campusoverflow.reputation.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BountyRepository {
    Optional<Bounty> findOpenByQuestion(long questionId);

    Optional<Bounty> findLatestByQuestion(long questionId);

    List<Bounty> findDue(Instant now, int limit);

    Bounty save(Bounty bounty);

    /** 条件更新：仅当数据库中仍为 OPEN 时置为 AWARDED，返回是否抢到终态（防并发重复结算）。 */
    boolean tryAward(long bountyId, long answerId, long winnerId, Instant now);

    /** 条件更新：仅当数据库中仍为 OPEN 时置为 EXPIRED，返回是否抢到终态（防并发重复退款）。 */
    boolean tryExpire(long bountyId, Instant now);

    /** 标记「获奖后判错，待复核」；不影响资金。 */
    void markDisputed(long bountyId);
}
