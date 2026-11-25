package com.campusoverflow.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** B05-2 新增领域规则：展示下限、冲正、积分分账、待追回。 */
class ReputationB05RulesTest {

    private static final Instant NOW = Instant.parse("2026-11-25T02:00:00Z");

    @Test
    void displayReputationClampsNegativeButKeepsLedgerTruth() {
        ReputationAccount account = ReputationAccount.reconstitute(2, -7, 0, 0, 0);

        assertThat(account.displayReputation()).isZero();
        assertThat(account.reputation()).isEqualTo(-7);
        assertThat(ReputationRulebook.displayReputation(15)).isEqualTo(15);
    }

    @Test
    void reversalDeltaIsOppositeOfOriginal() {
        assertThat(ReputationRulebook.reversalDelta(15)).isEqualTo(-15);
        assertThat(ReputationRulebook.reversalDelta(-2)).isEqualTo(2);
    }

    @Test
    void endorsementPointsAreFive() {
        assertThat(ReputationRulebook.ENDORSEMENT_POINTS).isEqualTo(5);
    }

    @Test
    void reasonCurrencySeparatesReputationFromPoints() {
        assertThat(ReputationReason.ANSWER_ENDORSED.isPoints()).isTrue();
        assertThat(ReputationReason.ANSWER_ACCEPTED.isPoints()).isFalse();
        assertThat(ReputationReason.namesOf(ReputationReason.Currency.POINTS))
                .containsExactly("ANSWER_ENDORSED");
        assertThat(ReputationReason.namesOf(ReputationReason.Currency.REPUTATION))
                .containsExactly("ANSWER_ACCEPTED", "QUESTION_VOTE", "ANSWER_VOTE", "BOUNTY_AWARDED");
    }

    @Test
    void applyPointsCanGoNegativeAndFreezeIsNaturallyBlocked() {
        ReputationAccount account = ReputationAccount.open(2, 3);

        account.applyPoints(-5);

        assertThat(account.availablePoints()).isEqualTo(-2);
        assertThat(account.pendingRecovery()).isEqualTo(2);
        assertThatThrownBy(() -> account.freezePoints(1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("可用积分不足");
    }

    @Test
    void ledgerEntryCarriesReversalLink() {
        LedgerEntry reversal = LedgerEntry.of(2, "u1", ReputationReason.ANSWER_ACCEPTED, -15, "ANSWER", 50, NOW,
                "e1");
        LedgerEntry plain = LedgerEntry.of(2, "e1", ReputationReason.ANSWER_ACCEPTED, 15, "ANSWER", 50, NOW);

        assertThat(reversal.isReversal()).isTrue();
        assertThat(reversal.reversesEventId()).isEqualTo("e1");
        assertThat(plain.isReversal()).isFalse();
        assertThat(plain.reversesEventId()).isNull();
    }
}
