package com.campusoverflow.qa.application.query;

import java.time.Instant;

public record AnswerRow(long id, long authorId, String body, int score, Long endorsedBy, Instant createdAt,
                        Instant updatedAt, String source) {

    /** 旧调用点兼容：历史行一律视为人工回答。 */
    public AnswerRow(long id, long authorId, String body, int score, Long endorsedBy, Instant createdAt,
                      Instant updatedAt) {
        this(id, authorId, body, score, endorsedBy, createdAt, updatedAt, "HUMAN");
    }
}
