package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.domain.BountySource;
import com.campusoverflow.reputation.domain.BudgetAdjustment;
import com.campusoverflow.reputation.domain.BudgetAdjustmentSource;
import com.campusoverflow.reputation.domain.CourseBudget;
import com.campusoverflow.reputation.domain.CourseBudgetRepository;
import org.springframework.stereotype.Component;

/** 课程预算资金来源：冻结/支出/退款只动 rep_course_budget，每笔净增减记 rep_budget_adjustment。 */
@Component
public class CourseBudgetFunding implements BountyFunding {

    private final CourseBudgetRepository budgets;

    public CourseBudgetFunding(CourseBudgetRepository budgets) {
        this.budgets = budgets;
    }

    @Override
    public BountySource source() {
        return BountySource.COURSE_BUDGET;
    }

    @Override
    public void freeze(long sourceId, int points, BountyOperationContext context) {
        CourseBudget budget = budgets.findByCourseId(sourceId).orElseGet(() -> CourseBudget.open(sourceId));
        budget.freezePoints(points);
        budgets.save(budget);
    }

    @Override
    public void spend(long sourceId, int points, BountyOperationContext context) {
        CourseBudget budget = budgets.findByCourseId(sourceId).orElseGet(() -> CourseBudget.open(sourceId));
        budget.spendFrozen(points);
        budgets.save(budget);
        appendIfAbsent(sourceId, -points, BudgetAdjustmentSource.BOUNTY_SPEND, context, "悬赏结算支出");
    }

    @Override
    public void refund(long sourceId, int points, BountyOperationContext context) {
        CourseBudget budget = budgets.findByCourseId(sourceId).orElseGet(() -> CourseBudget.open(sourceId));
        budget.refundFrozen(points);
        budgets.save(budget);
        appendIfAbsent(sourceId, points, BudgetAdjustmentSource.BOUNTY_REFUND, context, "悬赏关闭退款");
    }

    private void appendIfAbsent(long courseId, int delta, BudgetAdjustmentSource source,
                                BountyOperationContext context, String reason) {
        if (budgets.exists(context.eventId(), courseId)) {
            return;
        }
        budgets.append(BudgetAdjustment.of(courseId, delta, source, context.sponsorId(), reason,
                context.eventId(), context.at()));
    }
}
