package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.domain.PointsRecovery;
import com.campusoverflow.reputation.domain.PointsRecoveryRepository;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：撤销后积分缺口的登记、复核。全程不自动扣款（D8），缺口由账户余额派生。 */
@Service
public class PointsRecoveryService implements PointsRecoveryRecorder {

    private final PointsRecoveryRepository recoveries;
    private final ReputationAccountRepository accounts;
    private final AuditTrail audit;
    private final Clock clock;

    public PointsRecoveryService(PointsRecoveryRepository recoveries, ReputationAccountRepository accounts,
                                 AuditTrail audit, Clock clock) {
        this.recoveries = recoveries;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    /** 同一个人同时只保留一条 PENDING；事件重放只更新缺口金额，不重复建记录。 */
    @Override
    @Transactional
    public void ensurePending(long userId, int amount, String sourceEventId) {
        if (amount <= 0) {
            return;
        }
        recoveries.findPendingByUser(userId).ifPresentOrElse(
                existing -> {
                    existing.raiseTo(amount);
                    recoveries.save(existing);
                },
                () -> recoveries.save(PointsRecovery.open(userId, amount, sourceEventId, clock.instant())));
    }

    @Transactional(readOnly = true)
    public PageResult<PointsRecoveryView> pending(PageRequest page) {
        return recoveries.findAll(page).map(this::toView);
    }

    @Transactional
    public PointsRecoveryView resolve(long id, Actor actor, PointsRecovery.Decision decision, String reason) {
        if (!actor.isAdmin()) {
            throw new ForbiddenException("REP-5006", "仅管理员可复核待追回记录");
        }
        PointsRecovery recovery = recoveries.findById(id)
                .orElseThrow(() -> new NotFoundException("REP-5007", "待追回记录不存在"));
        recovery.settle(decision, actor.userId(), reason, clock.instant());
        recoveries.save(recovery);
        audit.record("POINTS_RECOVERY_RESOLVED", actor.userId(), "USER",
                String.valueOf(recovery.userId()), "id=" + id + ",decision=" + decision);
        return toView(recovery);
    }

    private PointsRecoveryView toView(PointsRecovery r) {
        int gap = accounts.findByUserId(r.userId()).map(a -> a.pendingRecovery()).orElse(0);
        return new PointsRecoveryView(r.id() == null ? 0 : r.id(), r.userId(), r.amount(), r.status().name(),
                r.sourceEventId(), r.createdAt(), r.resolvedAt(), r.resolvedBy(), r.note(), gap);
    }
}
