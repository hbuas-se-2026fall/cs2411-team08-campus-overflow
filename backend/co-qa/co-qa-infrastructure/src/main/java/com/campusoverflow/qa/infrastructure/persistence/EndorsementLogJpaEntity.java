package com.campusoverflow.qa.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "qa_endorsement_log")
public class EndorsementLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "answer_id", nullable = false)
    private long answerId;

    @Column(name = "endorser_id", nullable = false)
    private long endorserId;

    @Column(name = "answer_version", nullable = false)
    private long answerVersion;

    @Column(nullable = false, length = 16)
    private String action;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EndorsementLogJpaEntity() {
    }

    public EndorsementLogJpaEntity(long answerId, long endorserId, long answerVersion, String action,
                                   String reason, Instant createdAt) {
        this.answerId = answerId;
        this.endorserId = endorserId;
        this.answerVersion = answerVersion;
        this.action = action;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public long getAnswerId() { return answerId; }
    public long getEndorserId() { return endorserId; }
    public long getAnswerVersion() { return answerVersion; }
    public String getAction() { return action; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
