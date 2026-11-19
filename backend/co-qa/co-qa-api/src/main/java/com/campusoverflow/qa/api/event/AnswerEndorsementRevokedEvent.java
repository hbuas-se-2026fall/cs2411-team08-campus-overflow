package com.campusoverflow.qa.api.event;

import com.campusoverflow.shared.event.IntegrationEvent;
import java.time.Instant;

/** 教师认证被撤销（人工撤销或答案编辑导致失效）。Reputation 据此冲正认证奖励，answerVersion 供幂等。 */
public record AnswerEndorsementRevokedEvent(String eventId, Instant occurredAt, long answerId, long questionId,
                                            long answerAuthorId, long revokedById, String reason,
                                            long answerVersion) implements IntegrationEvent {
}
