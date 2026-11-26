package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryAccounts;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryBadges;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryLedger;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.StubIdentity;
import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** B05-2「我的积分」端点背后的查询用例。 */
class PointsQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");
    private static final Actor SELF = new Actor(2, "小红", Role.STUDENT);

    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final InMemoryLedger ledger = new InMemoryLedger();
    private final PointsQueryService queries = new PointsQueryService(accounts, ledger);

    @Test
    void balanceShowsAvailableFrozenAndTotal() {
        accounts.save(ReputationAccount.reconstitute(2, 10, 30, 5, 0));

        PointsView view = queries.balance(SELF);

        assertThat(view.userId()).isEqualTo(2);
        assertThat(view.availablePoints()).isEqualTo(30);
        assertThat(view.frozenPoints()).isEqualTo(5);
        assertThat(view.totalPoints()).isEqualTo(35);
        assertThat(view.pendingRecovery()).isZero();
    }

    @Test
    void pendingRecoveryReflectsNegativeBalanceAfterReversal() {
        accounts.save(ReputationAccount.reconstitute(2, 0, -5, 0, 0));

        assertThat(queries.balance(SELF).pendingRecovery()).isEqualTo(5);
        assertThat(queries.balance(SELF).availablePoints()).isEqualTo(-5);
    }

    @Test
    void missingAccountReturnsZeroBalance() {
        PointsView view = queries.balance(SELF);

        assertThat(view.availablePoints()).isZero();
        assertThat(view.frozenPoints()).isZero();
        assertThat(view.totalPoints()).isZero();
        assertThat(view.pendingRecovery()).isZero();
    }

    @Test
    void historyOnlyReturnsPointsEntries() {
        ledger.append(LedgerEntry.of(2, "e1", ReputationReason.ANSWER_ACCEPTED, 15, "ANSWER", 50, NOW));
        ledger.append(LedgerEntry.of(2, "en1", ReputationReason.ANSWER_ENDORSED, 5, "ANSWER", 50, NOW));

        var result = queries.history(SELF, new PageRequest(1, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).reason()).isEqualTo("ANSWER_ENDORSED");
        assertThat(result.items().get(0).delta()).isEqualTo(5);
    }

    @Test
    void historyExposesReversalLink() {
        ledger.append(LedgerEntry.of(2, "en1", ReputationReason.ANSWER_ENDORSED, 5, "ANSWER", 50, NOW));
        ledger.append(LedgerEntry.of(2, "er1", ReputationReason.ANSWER_ENDORSED, -5, "ANSWER", 50, NOW, "en1"));

        var result = queries.history(SELF, new PageRequest(1, 20));

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(1).reversesEventId()).isEqualTo("en1");
        assertThat(result.items().get(0).reversesEventId()).isNull();
    }

    @Test
    void reputationLedgerExcludesPointsEntries() {
        ledger.append(LedgerEntry.of(2, "e1", ReputationReason.ANSWER_ACCEPTED, 15, "ANSWER", 50, NOW));
        ledger.append(LedgerEntry.of(2, "en1", ReputationReason.ANSWER_ENDORSED, 5, "ANSWER", 50, NOW));
        ReputationQueryService reputationQueries = new ReputationQueryService(accounts, ledger, new InMemoryBadges(),
                new StubIdentity());

        var result = reputationQueries.ledger(2, SELF, new PageRequest(1, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).reason()).isEqualTo("ANSWER_ACCEPTED");
    }
}
