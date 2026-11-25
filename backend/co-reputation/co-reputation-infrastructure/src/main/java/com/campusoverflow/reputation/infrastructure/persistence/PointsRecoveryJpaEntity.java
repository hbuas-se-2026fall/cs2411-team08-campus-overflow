package com.campusoverflow.reputation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "rep_points_recovery")
public class PointsRecoveryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "source_event_id", nullable = false, length = 64)
    private String sourceEventId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(length = 255)
    private String note;

    @Column(name = "pending_slot")
    private Long pendingSlot;

    protected PointsRecoveryJpaEntity() {
    }

    public PointsRecoveryJpaEntity(long userId, int amount, String status, String sourceEventId, Instant createdAt) {
        this.userId = userId;
        this.amount = amount;
        this.status = status;
        this.sourceEventId = sourceEventId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public long getUserId() { return userId; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSourceEventId() { return sourceEventId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getPendingSlot() { return pendingSlot; }
    public void setPendingSlot(Long pendingSlot) { this.pendingSlot = pendingSlot; }
}
