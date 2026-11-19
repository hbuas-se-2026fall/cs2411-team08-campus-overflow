package com.campusoverflow.qa.domain;

import com.campusoverflow.shared.domain.Guard;
import java.time.Instant;
import java.util.Objects;

/** 教师复核采纳的留痕记录：谁、在哪个版本、以什么理由撤销了采纳。 */
public class AcceptanceReview {

    private Long id;
    private final long questionId;
    private final long reviewerId;
    private final long questionVersion;
    private final String result;
    private final String reason;
    private final Instant createdAt;

    private AcceptanceReview(Long id, long questionId, long reviewerId, long questionVersion, String result,
                              String reason, Instant createdAt) {
        this.id = id;
        this.questionId = questionId;
        this.reviewerId = reviewerId;
        this.questionVersion = questionVersion;
        this.result = result;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public static AcceptanceReview approve(long questionId, long reviewerId, long questionVersion, String reason,
                                           Instant now) {
        return new AcceptanceReview(null, questionId, reviewerId, questionVersion, "APPROVED",
                Guard.requireText(reason, 1, 500, "QA-1031", "复核理由"), Objects.requireNonNull(now));
    }

    public static AcceptanceReview reconstitute(Long id, long questionId, long reviewerId, long questionVersion,
                                                 String result, String reason, Instant createdAt) {
        return new AcceptanceReview(id, questionId, reviewerId, questionVersion, result, reason, createdAt);
    }

    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID 已分配");
        }
        this.id = id;
    }

    public Long id() { return id; }
    public long questionId() { return questionId; }
    public long reviewerId() { return reviewerId; }
    public long questionVersion() { return questionVersion; }
    public String result() { return result; }
    public String reason() { return reason; }
    public Instant createdAt() { return createdAt; }
}
