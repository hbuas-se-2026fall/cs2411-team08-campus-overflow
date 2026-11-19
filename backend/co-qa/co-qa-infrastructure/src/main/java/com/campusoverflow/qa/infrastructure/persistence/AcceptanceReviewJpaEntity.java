package com.campusoverflow.qa.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "qa_acceptance_review")
public class AcceptanceReviewJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private long questionId;

    @Column(name = "reviewer_id", nullable = false)
    private long reviewerId;

    @Column(name = "question_version", nullable = false)
    private long questionVersion;

    @Column(nullable = false, length = 16)
    private String result;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AcceptanceReviewJpaEntity() {
    }

    public AcceptanceReviewJpaEntity(long questionId, long reviewerId, long questionVersion, String result,
                                     String reason, Instant createdAt) {
        this.questionId = questionId;
        this.reviewerId = reviewerId;
        this.questionVersion = questionVersion;
        this.result = result;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public long getQuestionId() { return questionId; }
    public long getReviewerId() { return reviewerId; }
    public long getQuestionVersion() { return questionVersion; }
    public String getResult() { return result; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
