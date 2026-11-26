package com.campusoverflow.reputation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "rep_budget_adjustment")
public class BudgetAdjustmentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private long courseId;

    @Column(nullable = false)
    private int delta;

    @Column(nullable = false, length = 24)
    private String source;

    @Column(name = "operator_id", nullable = false)
    private long operatorId;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BudgetAdjustmentJpaEntity() {
    }

    public BudgetAdjustmentJpaEntity(long courseId, int delta, String source, long operatorId, String reason,
                                     String eventId, Instant createdAt) {
        this.courseId = courseId;
        this.delta = delta;
        this.source = source;
        this.operatorId = operatorId;
        this.reason = reason;
        this.eventId = eventId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public long getCourseId() { return courseId; }
    public int getDelta() { return delta; }
    public String getSource() { return source; }
    public long getOperatorId() { return operatorId; }
    public String getReason() { return reason; }
    public String getEventId() { return eventId; }
    public Instant getCreatedAt() { return createdAt; }
}
