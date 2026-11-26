package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import java.time.Duration;
import java.time.Instant;

/**
 * 悬赏：OPEN →（问题采纳）→ AWARDED；OPEN →（到期 / 删除 / 自赢）→ EXPIRED。终态不可再变。
 * 资金来源由 BountySource 标记，冻结与终态操作交由 PointsWallet 策略完成（DIP）。
 */
public class Bounty {

    public static final int MIN_POINTS = 10;
    public static final int MAX_POINTS = 500;
    public static final int MAX_DAYS = 14;

    private Long id;
    private final long questionId;
    private final long sponsorId;
    private final int points;
    private final BountySource source;
    private final long sourceId;
    private final String commandKey;
    private BountyStatus status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Long winnerId;
    private Long answerId;
    private Instant closedAt;
    private boolean disputed;

    private Bounty(Long id, long questionId, long sponsorId, int points, BountySource source, long sourceId,
                   String commandKey, BountyStatus status, Instant createdAt, Instant expiresAt, Long winnerId,
                   Long answerId, Instant closedAt, boolean disputed) {
        this.id = id;
        this.questionId = questionId;
        this.sponsorId = sponsorId;
        this.points = points;
        this.source = source;
        this.sourceId = sourceId;
        this.commandKey = commandKey;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.winnerId = winnerId;
        this.answerId = answerId;
        this.closedAt = closedAt;
        this.disputed = disputed;
    }

    /** B05 既有签名：仅课程管理者可发起、资金来自个人账户。保留以兼容既有测试与调用方。 */
    public static Bounty open(long questionId, boolean questionOpen, Actor sponsor, boolean sponsorManagesCourse,
                              int points, int days, Instant now) {
        if (!sponsorManagesCourse) {
            throw new ForbiddenException("REP-2001", "只有课程管理者可以发起悬赏");
        }
        return open(questionId, questionOpen, sponsor, true, points, days, now,
                BountySource.PERSONAL, sponsor.userId(), null);
    }

    /**
     * B06 签名：授权结果由应用层按来源算好后传入（SoC），领域只校验不变量。
     * PERSONAL 的 sourceId 为发起人 userId，COURSE_BUDGET 的 sourceId 为课程 ID。
     */
    public static Bounty open(long questionId, boolean questionOpen, Actor sponsor, boolean allowed,
                              int points, int days, Instant now, BountySource source, long sourceId,
                              String commandKey) {
        if (!allowed) {
            throw new ForbiddenException("REP-2001", "无权为该问题发起该来源的悬赏");
        }
        if (points < MIN_POINTS || points > MAX_POINTS) {
            throw new BusinessRuleException("REP-2002", "悬赏积分应在 " + MIN_POINTS + "–" + MAX_POINTS + " 之间");
        }
        if (days < 1 || days > MAX_DAYS) {
            throw new BusinessRuleException("REP-2003", "悬赏期限应在 1–" + MAX_DAYS + " 天之间");
        }
        if (!questionOpen) {
            throw new BusinessRuleException("REP-2004", "问题已关闭或已采纳，不能发起悬赏");
        }
        return new Bounty(null, questionId, sponsor.userId(), points, source, sourceId, commandKey,
                BountyStatus.OPEN, now, now.plus(Duration.ofDays(days)), null, null, null, false);
    }

    /** B05 既有签名。 */
    public static Bounty reconstitute(Long id, long questionId, long sponsorId, int points, BountyStatus status,
                                      Instant createdAt, Instant expiresAt, Long winnerId, Long answerId,
                                      Instant closedAt) {
        return reconstitute(id, questionId, sponsorId, points, BountySource.PERSONAL, sponsorId, null,
                status, createdAt, expiresAt, winnerId, answerId, closedAt, false);
    }

    public static Bounty reconstitute(Long id, long questionId, long sponsorId, int points, BountySource source,
                                      long sourceId, String commandKey, BountyStatus status, Instant createdAt,
                                      Instant expiresAt, Long winnerId, Long answerId, Instant closedAt,
                                      boolean disputed) {
        return new Bounty(id, questionId, sponsorId, points, source, sourceId, commandKey, status, createdAt,
                expiresAt, winnerId, answerId, closedAt, disputed);
    }

    public void award(long answerId, long winnerId, Instant now) {
        ensureOpen();
        if (winnerId == sponsorId) {
            throw new BusinessRuleException("REP-2005", "发起人不能赢自己的悬赏");
        }
        this.status = BountyStatus.AWARDED;
        this.winnerId = winnerId;
        this.answerId = answerId;
        this.closedAt = now;
    }

    /** 关闭并退款：到期未采纳、问题被删除，或被采纳的恰好是发起人自己的回答。 */
    public void close(Instant now) {
        ensureOpen();
        this.status = BountyStatus.EXPIRED;
        this.closedAt = now;
    }

    /** 获奖后判错：只留痕进复核，不回退结算、不强扣积分（D8）。重复标记幂等。 */
    public void markDisputed() {
        if (status != BountyStatus.AWARDED) {
            throw new ConflictException("REP-2006", "悬赏未结算，不能标记争议");
        }
        this.disputed = true;
    }

    public boolean isDue(Instant now) {
        return status == BountyStatus.OPEN && !now.isBefore(expiresAt);
    }

    private void ensureOpen() {
        if (status != BountyStatus.OPEN) {
            throw new ConflictException("REP-2006", "悬赏已结束，不能再变更");
        }
    }

    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID 已分配");
        }
        this.id = id;
    }

    public Long id() { return id; }
    public long questionId() { return questionId; }
    public long sponsorId() { return sponsorId; }
    public int points() { return points; }
    public BountySource source() { return source; }
    public long sourceId() { return sourceId; }
    public String commandKey() { return commandKey; }
    public BountyStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public Long winnerId() { return winnerId; }
    public Long answerId() { return answerId; }
    public Instant closedAt() { return closedAt; }
    public boolean disputed() { return disputed; }
}
