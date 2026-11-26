package com.campusoverflow.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;

/** B06 课程预算：可用 + 冻结守恒，增减与冻结的边界。 */
class CourseBudgetTest {

    @Test
    void adjustAddsAvailablePoints() {
        CourseBudget budget = CourseBudget.open(1);

        assertThat(budget.availablePoints()).isZero();
        assertThat(budget.frozenPoints()).isZero();

        budget.adjust(500);

        assertThat(budget.availablePoints()).isEqualTo(500);
        assertThat(budget.frozenPoints()).isZero();
        assertThat(budget.courseId()).isEqualTo(1);
    }

    @Test
    void adjustRejectsZeroAndOverdraft() {
        CourseBudget budget = CourseBudget.open(1);

        assertThatThrownBy(() -> budget.adjust(0)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> budget.adjust(-1)).isInstanceOf(BusinessRuleException.class);

        budget.adjust(100);
        assertThatThrownBy(() -> budget.adjust(-101)).isInstanceOf(BusinessRuleException.class);
        budget.adjust(-100);
        assertThat(budget.availablePoints()).isZero();
    }

    @Test
    void freezeSpendAndRefundKeepConservation() {
        CourseBudget budget = CourseBudget.open(1);
        budget.adjust(200);

        budget.freezePoints(50);
        assertThat(budget.availablePoints()).isEqualTo(150);
        assertThat(budget.frozenPoints()).isEqualTo(50);

        budget.spendFrozen(50);
        assertThat(budget.availablePoints()).isEqualTo(150);
        assertThat(budget.frozenPoints()).isZero();

        budget.freezePoints(30);
        budget.refundFrozen(30);
        assertThat(budget.availablePoints()).isEqualTo(150);
        assertThat(budget.frozenPoints()).isZero();
    }

    @Test
    void walletRejectsInsufficientBalances() {
        CourseBudget budget = CourseBudget.open(1);
        budget.adjust(20);

        assertThatThrownBy(() -> budget.freezePoints(21)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> budget.freezePoints(0)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> budget.spendFrozen(1)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> budget.refundFrozen(1)).isInstanceOf(BusinessRuleException.class);

        budget.freezePoints(20);
        assertThatThrownBy(() -> budget.spendFrozen(21)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> budget.refundFrozen(21)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reconstitutePreservesBalances() {
        CourseBudget budget = CourseBudget.reconstitute(7, 80, 20, 3);

        assertThat(budget.courseId()).isEqualTo(7);
        assertThat(budget.availablePoints()).isEqualTo(80);
        assertThat(budget.frozenPoints()).isEqualTo(20);
        assertThat(budget.version()).isEqualTo(3);
    }

    @Test
    void budgetIsAPointsWallet() {
        assertThat(CourseBudget.open(1)).isInstanceOf(PointsWallet.class);
        assertThat(ReputationAccount.open(2, 10)).isInstanceOf(PointsWallet.class);
    }
}
