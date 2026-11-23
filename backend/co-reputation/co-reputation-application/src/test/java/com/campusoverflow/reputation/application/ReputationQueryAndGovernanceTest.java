package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.reputation.api.event.ReputationChangedEvent;
import com.campusoverflow.reputation.application.fake.ReputationFixtures;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryAccounts;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryBadges;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryLeaderboard;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryLedger;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryProcessed;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.StubIdentity;
import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReputationQueryAndGovernanceTest {

    private static final Instant NOW = ReputationFixtures.NOW;

    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final InMemoryLedger ledger = new InMemoryLedger();
    private final StubIdentity identity = new StubIdentity();
    private final InMemoryLeaderboard store = new InMemoryLeaderboard();
    private final InMemoryProcessed processed = new InMemoryProcessed();

    private final ReputationQueryService query = new ReputationQueryService(accounts, ledger, new InMemoryBadges(),
            identity);
    private final ReconciliationService reconciliation = new ReconciliationService(accounts, ledger);
    private final LeaderboardService leaderboard = new LeaderboardService(store, accounts, identity, processed,
            ReputationFixtures.fixedClock());

    @Test
    void profileHidesPointsFromOthersButShowsToSelf() {
        accounts.save(ReputationAccount.reconstitute(2, 15, 30, 0, 0));
        identity.user(2, "小红", Role.STUDENT);

        assertThat(query.profile(2, new Actor(1, "小明", Role.STUDENT)).availablePoints()).isNull();
        assertThat(query.profile(2, new Actor(2, "小红", Role.STUDENT)).availablePoints()).isEqualTo(30);
        assertThat(query.profile(2, new Actor(2, "小红", Role.STUDENT)).reputation()).isEqualTo(15);
    }

    @Test
    void ledgerRejectsUnauthorizedViewer() {
        identity.user(2, "小红", Role.STUDENT);

        assertThatThrownBy(() -> query.ledger(2, new Actor(1, "小明", Role.STUDENT), new PageRequest(1, 20)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminCanReadOthersLedger() {
        identity.user(2, "小红", Role.STUDENT);
        ledger.append(LedgerEntry.of(2, "e1", ReputationReason.ANSWER_ACCEPTED, 15, "ANSWER", 50, NOW));

        assertThat(query.ledger(2, new Actor(99, "管理员", Role.ADMIN), new PageRequest(1, 20)).items()).hasSize(1);
    }

    @Test
    void reconciliationDetectsAccountLedgerMismatch() {
        accounts.save(ReputationAccount.reconstitute(2, 15, 0, 0, 0));
        accounts.save(ReputationAccount.reconstitute(3, 10, 0, 0, 0));
        ledger.append(LedgerEntry.of(3, "e1", ReputationReason.ANSWER_VOTE, 10, "ANSWER", 1, NOW));

        List<ReconciliationService.Mismatch> mismatches = reconciliation.reconcile();

        assertThat(mismatches).extracting(ReconciliationService.Mismatch::userId).containsExactly(2L);
    }

    @Test
    void leaderboardEventIsDeduplicated() {
        ReputationChangedEvent event = new ReputationChangedEvent("r1", NOW, 2, 15, 15, "ANSWER_ACCEPTED");

        leaderboard.on(event);
        leaderboard.on(event);

        assertThat(store.increments).containsExactly("2:15");
    }
}
