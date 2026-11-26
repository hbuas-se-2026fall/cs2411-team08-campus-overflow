package com.campusoverflow.reputation.infrastructure.persistence;

import com.campusoverflow.reputation.domain.BudgetAdjustment;
import com.campusoverflow.reputation.domain.BudgetAdjustmentSource;
import com.campusoverflow.reputation.domain.CourseBudget;
import com.campusoverflow.reputation.domain.CourseBudgetRepository;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** 课程预算与预算流水的持久化适配器（同一聚合，一个适配器）。 */
@Repository
public class CourseBudgetPersistenceAdapter implements CourseBudgetRepository {

    private final CourseBudgetJpaRepository budgets;
    private final BudgetAdjustmentJpaRepository adjustments;

    public CourseBudgetPersistenceAdapter(CourseBudgetJpaRepository budgets,
                                          BudgetAdjustmentJpaRepository adjustments) {
        this.budgets = budgets;
        this.adjustments = adjustments;
    }

    @Override
    public Optional<CourseBudget> findByCourseId(long courseId) {
        return budgets.findById(courseId).map(e -> CourseBudget.reconstitute(courseId, e.getAvailablePoints(),
                e.getFrozenPoints(), 0));
    }

    @Override
    public CourseBudget save(CourseBudget budget) {
        CourseBudgetJpaEntity e = budgets.findById(budget.courseId())
                .orElseGet(() -> new CourseBudgetJpaEntity(budget.courseId(), 0, 0));
        e.setAvailablePoints(budget.availablePoints());
        e.setFrozenPoints(budget.frozenPoints());
        budgets.saveAndFlush(e);
        return budget;
    }

    @Override
    public void append(BudgetAdjustment adjustment) {
        adjustments.saveAndFlush(new BudgetAdjustmentJpaEntity(adjustment.courseId(), adjustment.delta(),
                adjustment.source().name(), adjustment.operatorId(), adjustment.reason(), adjustment.eventId(),
                adjustment.createdAt()));
    }

    @Override
    public PageResult<BudgetAdjustment> findAdjustments(long courseId, PageRequest page) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page.page() - 1, page.size());
        List<BudgetAdjustment> items = adjustments.findByCourseIdOrderByIdDesc(courseId, pageable).stream()
                .map(CourseBudgetPersistenceAdapter::toDomain).toList();
        return new PageResult<>(items, adjustments.countByCourseId(courseId), page.page(), page.size());
    }

    @Override
    public boolean exists(String eventId, long courseId) {
        return adjustments.existsByEventIdAndCourseId(eventId, courseId);
    }

    private static BudgetAdjustment toDomain(BudgetAdjustmentJpaEntity e) {
        return BudgetAdjustment.reconstitute(e.getId(), e.getCourseId(), e.getDelta(),
                BudgetAdjustmentSource.valueOf(e.getSource()), e.getOperatorId(), e.getReason(), e.getEventId(),
                e.getCreatedAt());
    }
}
