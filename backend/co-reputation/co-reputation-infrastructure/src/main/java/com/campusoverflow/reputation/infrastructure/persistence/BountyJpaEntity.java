package com.campusoverflow.reputation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "rep_bounty")
public class BountyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private long questionId;

    @Column(name = "sponsor_id", nullable = false)
    private long sponsorId;

    @Column(nullable = false)
    private int points;

    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType = "PERSONAL";

    @Column(name = "source_id", nullable = false)
    private long sourceId;

    @Column(name = "command_key", length = 128)
    private String commandKey;

    @Column(nullable = false)
    private boolean disputed;

    @Column(name = "open_slot")
    private Long openSlot;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "winner_id")
    private Long winnerId;

    @Column(name = "answer_id")
    private Long answerId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private long version;

    protected BountyJpaEntity() {
    }

    public BountyJpaEntity(long questionId, long sponsorId, int points, Instant createdAt, Instant expiresAt) {
        this.questionId = questionId;
        this.sponsorId = sponsorId;
        this.points = points;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public long getQuestionId() { return questionId; }
    public long getSponsorId() { return sponsorId; }
    public int getPoints() { return points; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public long getSourceId() { return sourceId; }
    public void setSourceId(long sourceId) { this.sourceId = sourceId; }
    public String getCommandKey() { return commandKey; }
    public void setCommandKey(String commandKey) { this.commandKey = commandKey; }
    public boolean isDisputed() { return disputed; }
    public void setDisputed(boolean disputed) { this.disputed = disputed; }
    public Long getOpenSlot() { return openSlot; }
    public void setOpenSlot(Long openSlot) { this.openSlot = openSlot; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Long getWinnerId() { return winnerId; }
    public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }
    public Long getAnswerId() { return answerId; }
    public void setAnswerId(Long answerId) { this.answerId = answerId; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
}
