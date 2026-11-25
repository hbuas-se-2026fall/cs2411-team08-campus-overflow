package com.campusoverflow.reputation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 只追加：应用代码中不存在对本表的 UPDATE/DELETE。 */
@Entity
@Table(name = "rep_ledger")
public class LedgerJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(nullable = false, length = 32)
    private String reason;

    @Column(nullable = false)
    private int delta;

    @Column(name = "ref_type", length = 16)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private long refId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 冲正流水指向的原流水 event_id；普通流水为 null。 */
    @Column(name = "reverses_event_id", length = 64)
    private String reversesEventId;

    protected LedgerJpaEntity() {
    }

    public LedgerJpaEntity(long userId, String eventId, String reason, int delta, String refType, long refId,
                           Instant createdAt) {
        this(userId, eventId, reason, delta, refType, refId, createdAt, null);
    }

    public LedgerJpaEntity(long userId, String eventId, String reason, int delta, String refType, long refId,
                           Instant createdAt, String reversesEventId) {
        this.userId = userId;
        this.eventId = eventId;
        this.reason = reason;
        this.delta = delta;
        this.refType = refType;
        this.refId = refId;
        this.createdAt = createdAt;
        this.reversesEventId = reversesEventId;
    }

    public Long getId() { return id; }
    public long getUserId() { return userId; }
    public String getEventId() { return eventId; }
    public String getReason() { return reason; }
    public int getDelta() { return delta; }
    public String getRefType() { return refType; }
    public long getRefId() { return refId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getReversesEventId() { return reversesEventId; }
}
