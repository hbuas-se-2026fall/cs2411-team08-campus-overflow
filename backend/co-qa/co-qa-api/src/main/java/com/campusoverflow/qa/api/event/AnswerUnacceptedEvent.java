package com.campusoverflow.qa.api.event;

import com.campusoverflow.shared.event.IntegrationEvent;
import java.time.Instant;

/** 最佳答案被撤销采纳。Reputation 据此对原采纳奖励做相反流水冲正，不删账。 */
public record AnswerUnacceptedEvent(String eventId, Instant occurredAt, long answerId, long questionId,
                                     long answerAuthorId, long questionAuthorId, long revokedById,
                                     String questionTitle) implements IntegrationEvent {
}
