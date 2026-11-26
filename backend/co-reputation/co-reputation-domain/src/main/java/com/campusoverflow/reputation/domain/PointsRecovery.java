package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.domain.ConflictException;
import java.time.Instant;

/**
 * 撤销认证后「已花费积分」的待追回记录。只记录缺口、限制支出，绝不自动从用户账户强扣（D8）。
 * 余额回升时自动置 RESOLVED；管理员也可复核后标记 RECOVERED / WAIVED。
 */
public class PointsRecovery {

    public enum Decision { RECOVERED, WAIVED }

    private Long id;
    private final long userId;
    private int amount;
    private PointsRecoveryStatus status;
    private final String sourceEventId;
    private final Instant createdAt;
    private Instant resolvedAt;
    private Long resolvedBy;
    private String note;

    private PointsRecovery(Long id, long userId, int amount, PointsRecoveryStatus status, String sourceEventId,
                           Instant createdAt, Instant resolvedAt, Long resolvedBy, String note) {
        this.id = id;
        this.userId = userId;
        this.amount = amount;
        this.status = status;
        this.sourceEventId = sourceEventId;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
        this.note = note;
    }

    public static PointsRecovery open(long userId, int amount, String sourceEventId, Instant now) {
        return new PointsRecovery(null, userId, amount, PointsRecoveryStatus.PENDING, sourceEventId, now,
                null, null, null);
    }

    public static PointsRecovery reconstitute(Long id, long userId, int amount, PointsRecoveryStatus status,
                                              String sourceEventId, Instant createdAt, Instant resolvedAt,
                                              Long resolvedBy, String note) {
        return new PointsRecovery(id, userId, amount, status, sourceEventId, createdAt, resolvedAt, resolvedBy, note);
    }

    /** 同一用户同时只保留一条 PENDING；缺口扩大时取较大值（幂等）。 */
    public void raiseTo(int newAmount) {
        ensurePending();
        if (newAmount > amount) {
            amount = newAmount;
        }
    }

    /** 余额回升到非负时自动冲销。 */
    public void resolveAutomatically(Instant now) {
        ensurePending();
        status = PointsRecoveryStatus.RESOLVED;
        resolvedAt = now;
        note = "余额回升，自动冲销";
    }

    public void settle(Decision decision, long operatorId, String reason, Instant now) {
        ensurePending();
        status = decision == Decision.WAIVED ? PointsRecoveryStatus.WAIVED : PointsRecoveryStatus.RESOLVED;
        resolvedAt = now;
        resolvedBy = operatorId;
        note = reason;
    }

    private void ensurePending() {
        if (status != PointsRecoveryStatus.PENDING) {
            throw new ConflictException("REP-5007", "待追回记录已处理，不能重复操作");
        }
    }

    public void assignId(long value) {
        if (id != null) {
            throw new IllegalStateException("ID 已分配");
        }
        id = value;
    }

    public Long id() { return id; }
    public long userId() { return userId; }
    public int amount() { return amount; }
    public PointsRecoveryStatus status() { return status; }
    public String sourceEventId() { return sourceEventId; }
    public Instant createdAt() { return createdAt; }
    public Instant resolvedAt() { return resolvedAt; }
    public Long resolvedBy() { return resolvedBy; }
    public String note() { return note; }
}
