package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsementRevokedEvent;
import com.campusoverflow.reputation.application.fake.ReputationFixtures;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryAccounts;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryBadges;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryBounties;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryLedger;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryRecoveries;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.MutableClock;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.RecordingAudit;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.RecordingEvents;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.StubIdentity;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.StubQa;
import com.campusoverflow.reputation.domain.PointsRecovery;
import com.campusoverflow.reputation.domain.ReputationAccount;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** B06 × B05-2 联动：撤销认证后积分已花费 → 自动登记待追回，且不删除原流水。 */
class PointsRecoveryRecorderIntegrationTest {

    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final InMemoryLedger ledger = new InMemoryLedger();
    private final InMemoryBadges badges = new InMemoryBadges();
    private final RecordingEvents events = new RecordingEvents();
    private final MutableClock clock = new MutableClock(ReputationFixtures.NOW, ZoneOffset.UTC);
    private final InMemoryRecoveries recoveries = new InMemoryRecoveries();
    private final RecordingAudit audit = new RecordingAudit();
    private final BadgeService badgeService = new BadgeService(badges, events, clock);
    private final ReputationLedgerService ledgerService = new ReputationLedgerService(accounts, ledger, badgeService,
            events, clock);
    private final BountyService bountyService = new BountyService(new InMemoryBounties(), accounts, ledgerService,
            new StubQa(), new StubIdentity(), events, audit, clock);
    private final PointsRecoveryService recovery = new PointsRecoveryService(recoveries, accounts, audit, clock);
    private final ReputationEventHandlers handlers = new ReputationEventHandlers(accounts, ledger, ledgerService,
            badgeService, bountyService, clock, recovery::ensurePending);

    @Test
    void revokedEndorsementRegistersPendingRecoveryWhenPointsAlreadySpent() {
        accounts.save(ReputationAccount.open(2, 0));
        handlers.on(new AnswerEndorsedEvent("en1", ReputationFixtures.NOW, 50, 10, 2, 9, "问题10"));
        assertThat(accounts.get(2).availablePoints()).isEqualTo(5);
        // 模拟用户已把这 5 积分花掉
        accounts.get(2).applyPoints(-5);
        assertThat(accounts.get(2).availablePoints()).isZero();

        handlers.on(new AnswerEndorsementRevokedEvent("rv1", ReputationFixtures.NOW, 50, 10, 2, 9,
                "答案已编辑", 2));

        assertThat(accounts.get(2).availablePoints()).isEqualTo(-5);
        assertThat(accounts.get(2).pendingRecovery()).isEqualTo(5);
        assertThat(recoveries.findPendingByUser(2)).isPresent()
                .get().extracting(PointsRecovery::amount).isEqualTo(5);
        // 原流水仍在，只追加了冲正流水
        assertThat(ledger.entries).hasSize(2);
        assertThat(ledger.entries.get(1).reversesEventId()).isEqualTo("en1");
    }

    @Test
    void revokedEndorsementWithSufficientBalanceCreatesNoRecovery() {
        accounts.save(ReputationAccount.open(2, 100));
        handlers.on(new AnswerEndorsedEvent("en2", ReputationFixtures.NOW, 50, 10, 2, 9, "问题10"));

        handlers.on(new AnswerEndorsementRevokedEvent("rv2", ReputationFixtures.NOW, 50, 10, 2, 9,
                "撤销", 2));

        assertThat(accounts.get(2).availablePoints()).isEqualTo(100);
        assertThat(recoveries.findPendingByUser(2)).isEmpty();
    }

    @Test
    void replayedRevocationDoesNotDuplicateRecovery() {
        accounts.save(ReputationAccount.open(2, 0));
        handlers.on(new AnswerEndorsedEvent("en3", ReputationFixtures.NOW, 50, 10, 2, 9, "问题10"));
        accounts.get(2).applyPoints(-5);
        AnswerEndorsementRevokedEvent revoked = new AnswerEndorsementRevokedEvent("rv3", ReputationFixtures.NOW,
                50, 10, 2, 9, "撤销", 2);

        handlers.on(revoked);
        handlers.on(revoked);

        assertThat(recoveries.findAll(new com.campusoverflow.shared.page.PageRequest(1, 20)).items()).hasSize(1);
        assertThat(ledger.entries).hasSize(2);
    }
}
