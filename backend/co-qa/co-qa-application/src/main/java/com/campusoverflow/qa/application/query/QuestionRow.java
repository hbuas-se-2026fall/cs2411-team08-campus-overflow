package com.campusoverflow.qa.application.query;

import java.time.Instant;
import java.util.List;

public record QuestionRow(long id, long authorId, Long courseId, String title, String body, List<String> tags,
                          Long acceptedAnswerId, int score, int answerCount, int viewCount, Instant createdAt,
                          Instant updatedAt, String status) {

    /** 旧调用点兼容：未携带状态时按 OPEN 处理。 */
    public QuestionRow(long id, long authorId, Long courseId, String title, String body, List<String> tags,
                       Long acceptedAnswerId, int score, int answerCount, int viewCount, Instant createdAt,
                       Instant updatedAt) {
        this(id, authorId, courseId, title, body, tags, acceptedAnswerId, score, answerCount, viewCount,
                createdAt, updatedAt, "OPEN");
    }
}
