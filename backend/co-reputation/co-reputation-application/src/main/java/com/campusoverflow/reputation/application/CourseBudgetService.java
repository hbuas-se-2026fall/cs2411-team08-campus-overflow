package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.reputation.domain.BudgetAdjustment;
import com.campusoverflow.reputation.domain.BudgetAdjustmentSource;
import com.campusoverflow.reputation.domain.CourseBudget;
import com.campusoverflow.reputation.domain.CourseBudgetRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.command.CommandIdempotencyStore;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：课程预算查询与管理员分配/回收。课程存在性经 IdentityApi 校验（FF-2）。 */
@Service
public class CourseBudgetService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final CourseBudgetRepository budgets;
    private final IdentityApi identity;
    private final CommandIdempotencyStore idempotency;
    private final AuditTrail audit;
    private final Clock clock;

    public CourseBudgetService(CourseBudgetRepository budgets, IdentityApi identity,
                               CommandIdempotencyStore idempotency, AuditTrail audit, Clock clock) {
        this.budgets = budgets;
        this.identity = identity;
        this.idempotency = idempotency;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BudgetView budget(long courseId, Actor viewer) {
        CourseSummary course = requireCourse(courseId);
        authorizeRead(courseId, viewer);
        CourseBudget budget = budgets.findByCourseId(courseId).orElseGet(() -> CourseBudget.open(courseId));
        List<BudgetAdjustmentView> recent = budgets.findAdjustments(courseId, new PageRequest(1, 20))
                .items().stream().map(BudgetAdjustmentView::from).toList();
        return new BudgetView(courseId, course.name(), budget.availablePoints(), budget.frozenPoints(),
                budget.availablePoints() + budget.frozenPoints(), recent);
    }

    /** 管理员分配/回收预算。commandId 保证同一请求重放不重复记账。 */
    @Transactional
    public BudgetView adjust(Actor actor, long courseId, int delta, String reason, String commandId) {
        if (!actor.isAdmin()) {
            throw new ForbiddenException("REP-5006", "仅管理员可调整课程预算");
        }
        requireCourse(courseId);
        if (delta == 0) {
            throw new BusinessRuleException("REP-5004", "调整金额必须非零");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("REP-5004", "调整原因不能为空");
        }
        String key = "budget:adjust:" + (commandId == null || commandId.isBlank() ? EventIds.next() : commandId);
        CommandIdempotencyStore.BeginOutcome outcome = idempotency.begin(key, IDEMPOTENCY_TTL, clock.instant());
        if (outcome == CommandIdempotencyStore.BeginOutcome.IN_PROGRESS) {
            throw new ConflictException("REP-2007", "预算调整正在处理中，请稍后重试");
        }
        if (outcome == CommandIdempotencyStore.BeginOutcome.ALREADY_SUCCEEDED) {
            return budget(courseId, actor);
        }
        CourseBudget budget = budgets.findByCourseId(courseId).orElseGet(() -> CourseBudget.open(courseId));
        budget.adjust(delta);
        budgets.save(budget);
        budgets.append(BudgetAdjustment.of(courseId, delta, BudgetAdjustmentSource.ADMIN_ALLOCATION,
                actor.userId(), reason, key, clock.instant()));
        audit.record("BUDGET_ADJUSTED", actor.userId(), "COURSE", String.valueOf(courseId),
                "delta=" + delta + ",reason=" + reason);
        idempotency.complete(key, String.valueOf(courseId), clock.instant());
        return new BudgetView(courseId, requireCourse(courseId).name(), budget.availablePoints(),
                budget.frozenPoints(), budget.availablePoints() + budget.frozenPoints(), List.of());
    }

    private CourseSummary requireCourse(long courseId) {
        return identity.findCourse(courseId)
                .orElseThrow(() -> new NotFoundException("REP-5005", "课程不存在"));
    }

    private void authorizeRead(long courseId, Actor viewer) {
        if (viewer == null) {
            throw new ForbiddenException("REP-5008", "需要登录才能查看课程预算");
        }
        if (viewer.isAdmin() || identity.canManageCourse(viewer.userId(), courseId)
                || identity.isCourseMember(viewer.userId(), courseId)) {
            return;
        }
        throw new ForbiddenException("REP-5008", "无权查看该课程预算");
    }
}
