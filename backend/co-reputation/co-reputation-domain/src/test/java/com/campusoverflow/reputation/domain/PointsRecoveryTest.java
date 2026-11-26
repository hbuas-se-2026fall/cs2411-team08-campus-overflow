package com.campusoverflow.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.ConflictException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** B06 待追回聚合：幂等登记、复核、自动冲销。 */
class PointsRecoveryTest {

    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");

    @Test
    void openIsPendingAndRaiseToTakesMax() {
        PointsRecovery recovery = PointsRecovery.open(2, 5, "revoke-1", NOW);

        assertThat(recovery.status()).isEqualTo(PointsRecoveryStatus.PENDING);
        assertThat(recovery.amount()).isEqualTo(5);
        assertThat(recovery.sourceEventId()).isEqualTo("revoke-1");
        assertThat(recovery.createdAt()).isEqualTo(NOW);
        assertThat(recovery.resolvedAt()).isNull();

        recovery.raiseTo(3);
        assertThat(recovery.amount()).isEqualTo(5);
        recovery.raiseTo(9);
        assertThat(recovery.amount()).isEqualTo(9);
    }

    @Test
    void settleRecordsDecisionAndRejectsSecondTime() {
        PointsRecovery recovery = PointsRecovery.open(2, 5, "revoke-1", NOW);

        recovery.settle(PointsRecovery.Decision.RECOVERED, 99, "已线下追回", NOW.plusSeconds(60));

        assertThat(recovery.status()).isEqualTo(PointsRecoveryStatus.RESOLVED);
        assertThat(recovery.resolvedBy()).isEqualTo(99L);
        assertThat(recovery.resolvedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(recovery.note()).isEqualTo("已线下追回");
        assertThatThrownBy(() -> recovery.settle(PointsRecovery.Decision.WAIVED, 99, "重复", NOW))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void waiveSetsWaivedStatus() {
        PointsRecovery recovery = PointsRecovery.open(2, 5, "revoke-1", NOW);

        recovery.settle(PointsRecovery.Decision.WAIVED, 99, "豁免", NOW);

        assertThat(recovery.status()).isEqualTo(PointsRecoveryStatus.WAIVED);
    }

    @Test
    void automaticResolveClosesPending() {
        PointsRecovery recovery = PointsRecovery.open(2, 5, "revoke-1", NOW);

        recovery.resolveAutomatically(NOW.plusSeconds(10));

        assertThat(recovery.status()).isEqualTo(PointsRecoveryStatus.RESOLVED);
        assertThat(recovery.note()).contains("自动冲销");
        assertThatThrownBy(() -> recovery.raiseTo(1)).isInstanceOf(ConflictException.class);
    }

    @Test
    void reconstitutePreservesAllFields() {
        PointsRecovery recovery = PointsRecovery.reconstitute(7L, 2, 8, PointsRecoveryStatus.WAIVED,
                "revoke-1", NOW, NOW.plusSeconds(5), 99L, "豁免");

        assertThat(recovery.id()).isEqualTo(7L);
        assertThat(recovery.userId()).isEqualTo(2L);
        assertThat(recovery.amount()).isEqualTo(8);
        assertThat(recovery.status()).isEqualTo(PointsRecoveryStatus.WAIVED);
        assertThat(recovery.resolvedBy()).isEqualTo(99L);
        assertThat(recovery.note()).isEqualTo("豁免");
    }

    @Test
    void assignIdOnlyOnce() {
        PointsRecovery recovery = PointsRecovery.open(2, 5, "revoke-1", NOW);
        recovery.assignId(1);

        assertThat(recovery.id()).isEqualTo(1L);
        assertThatThrownBy(() -> recovery.assignId(2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void budgetAdjustmentFactoriesKeepValues() {
        BudgetAdjustment created = BudgetAdjustment.of(3, 100, BudgetAdjustmentSource.ADMIN_ALLOCATION, 9,
                "期初分配", "cmd-1", NOW);
        BudgetAdjustment restored = BudgetAdjustment.reconstitute(1L, 3, 100,
                BudgetAdjustmentSource.ADMIN_ALLOCATION, 9, "期初分配", "cmd-1", NOW);

        assertThat(created.id()).isNull();
        assertThat(created.courseId()).isEqualTo(3);
        assertThat(created.delta()).isEqualTo(100);
        assertThat(created.source()).isEqualTo(BudgetAdjustmentSource.ADMIN_ALLOCATION);
        assertThat(restored.id()).isEqualTo(1L);
        assertThat(restored.reason()).isEqualTo("期初分配");
    }
}
