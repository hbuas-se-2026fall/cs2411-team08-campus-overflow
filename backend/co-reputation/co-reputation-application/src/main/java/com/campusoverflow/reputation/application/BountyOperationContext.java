package com.campusoverflow.reputation.application;

import java.time.Instant;

/** 悬赏资金操作的上下文，供各来源策略记录流水（幂等键、原因、时间）。 */
public record BountyOperationContext(long bountyId, long questionId, long sponsorId, String eventId, Instant at) {
}
