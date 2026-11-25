package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsementRevokedEvent;
import com.campusoverflow.qa.api.event.AnswerUnacceptedEvent;
import com.campusoverflow.reputation.api.event.ReputationChangedEvent;
import com.campusoverflow.reputation.application.fake.ReputationFixtures;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryAccounts;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryBadges;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryBounties;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryLedger;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.RecordingAudit;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.RecordingEvents;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.StubIdentity;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.StubQa;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationReason;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** B05-2 撤销冲正闭环 + 声誉/积分分账。 */
class ReputationReversalTest {

    private static final Instant NOW = ReputationFixtures.NOW;

    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final InMemoryLedger ledger = new InMemoryLedger();
    private final InMemoryBadges badges = new InMemoryBadges();
    private final RecordingEvents events = new RecordingEvents();
    private final RecordingAudit audit = new RecordingAudit();
    private final StubIdentity identity = new StubIdentity();
    private final StubQa qa = new StubQa();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final BadgeService badgeService = new BadgeService(badges, events, clock);
    private final ReputationLedgerService ledgerService = new ReputationLedgerService(accounts, ledger, badgeService,
            events, clock);
    private final BountyService bountyService = new BountyService(new InMemoryBounties(), accounts, ledgerService, qa,
            identity, events, audit, clock);
    private final ReputationEventHandlers handlers = new ReputationEventHandlers(accounts, ledger, ledgerService,
            badgeService, bountyService, clock);
    private final ReconciliationService reconciliation = new ReconciliationService(accounts, ledger);

    private static AnswerAcceptedEvent accepted(String eventId, long authorId, long questionAuthorId) {
        return new AnswerAcceptedEvent(eventId, NOW, 50, 10, authorId, questionAuthorId, 1, "标题");
    }

    @Test
    void unacceptingAcceptedAnswerAppendsNegativeEntryLinkedToOriginal() {
        accounts.save(ReputationAccount.open(2, 0));
        handlers.on(accepted("e1", 2, 1));

        handlers.on(new AnswerUnacceptedEvent("u1", NOW, 50, 10, 2, 1, 1, "标题"));

        assertThat(accounts.get(2).reputation()).isZero();
        assertThat(ledger.entries).hasSize(2);
        LedgerEntry reversal = ledger.entries.get(1);
        assertThat(reversal.delta()).isEqualTo(-15);
        assertThat(reversal.reason()).isEqualTo(ReputationReason.ANSWER_ACCEPTED);
        assertThat(reversal.isReversal()).isTrue();
        assertThat(reversal.reversesEventId()).isEqualTo("e1");
        assertThat(ledger.entries.get(0).delta()).isEqualTo(15);
        assertThat(ledger.entries.get(0).isReversal()).isFalse();
    }

    @Test
    void replayingReversalEventIsIdempotent() {
        accounts.save(ReputationAccount.open(2, 0));
        handlers.on(accepted("e1", 2, 1));
        AnswerUnacceptedEvent reversal = new AnswerUnacceptedEvent("u1", NOW, 50, 10, 2, 1, 1, "标题");

        handlers.on(reversal);
        handlers.on(reversal);

        assertThat(accounts.get(2).reputation()).isZero();
        assertThat(ledger.entries).hasSize(2);
    }

    @Test
    void secondReversalEventCannotReverseSameOriginalTwice() {
        accounts.save(ReputationAccount.open(2, 0));
        handlers.on(accepted("e1", 2, 1));

        handlers.on(new AnswerUnacceptedEvent("u1", NOW, 50, 10, 2, 1, 1, "标题"));
        handlers.on(new AnswerUnacceptedEvent("u2", NOW, 50, 10, 2, 1, 1, "标题"));

        assertThat(accounts.get(2).reputation()).isZero();
        assertThat(ledger.entries).hasSize(2);
    }

    @Test
    void selfAcceptedAnswerProducesNoReversal() {
        accounts.save(ReputationAccount.open(2, 0));
        handlers.on(accepted("e1", 2, 2));

        handlers.on(new AnswerUnacceptedEvent("u1", NOW, 50, 10, 2, 2, 2, "标题"));

        assertThat(ledger.entries).isEmpty();
        assertThat(accounts.get(2).reputation()).isZero();
    }

    @Test
    void badgesAreNotRevokedOnUnaccept() {
        accounts.save(ReputationAccount.open(2, 0));
        handlers.on(accepted("e1", 2, 1));
        assertThat(badges.has(2, BadgeType.FIRST_ACCEPTED)).isTrue();

        handlers.on(new AnswerUnacceptedEvent("u1", NOW, 50, 10, 2, 1, 1, "标题"));

        assertThat(badges.has(2, BadgeType.FIRST_ACCEPTED)).isTrue();
    }

    @Test
    void reversalPublishesNegativeReputationChangedEvent() {
        accounts.save(ReputationAccount.open(2, 0));
        handlers.on(accepted("e1", 2, 1));

        handlers.on(new AnswerUnacceptedEvent("u1", NOW, 50, 10, 2, 1, 1, "标题"));

        assertThat(events.ofType(ReputationChangedEvent.class))
                .extracting(ReputationChangedEvent::delta).containsExactly(15, -15);
    }

    @Test
    void endorsementCreditsFivePointsWithoutTouchingReputation() {
        accounts.save(ReputationAccount.open(2, 0));

        handlers.on(new AnswerEndorsedEvent("en1", NOW, 50, 10, 2, 9, "标题"));

        assertThat(accounts.get(2).availablePoints()).isEqualTo(5);
        assertThat(accounts.get(2).reputation()).isZero();
        assertThat(badges.has(2, BadgeType.TEACHER_ENDORSED)).isTrue();
        assertThat(events.ofType(ReputationChangedEvent.class)).isEmpty();
        assertThat(ledger.entries).hasSize(1);
        assertThat(ledger.entries.get(0).reason()).isEqualTo(ReputationReason.ANSWER_ENDORSED);
    }

    @Test
    void revokingEndorsementReversesPointsAndKeepsOnlyAppend() {
        accounts.save(ReputationAccount.open(2, 0));
        handlers.on(new AnswerEndorsedEvent("en1", NOW, 50, 10, 2, 9, "标题"));

        handlers.on(new AnswerEndorsementRevokedEvent("er1", NOW, 50, 10, 2, 9, "误认证", 1));

        assertThat(accounts.get(2).availablePoints()).isZero();
        assertThat(ledger.entries).hasSize(2);
        assertThat(ledger.entries.get(1).delta()).isEqualTo(-5);
        assertThat(ledger.entries.get(1).reversesEventId()).isEqualTo("en1");
    }

    @Test
    void pointsEntriesDoNotBreakReputationReconciliation() {
        accounts.save(ReputationAccount.open(2, 0));
        handlers.on(accepted("e1", 2, 1));
        handlers.on(new AnswerEndorsedEvent("en1", NOW, 50, 10, 2, 9, "标题"));

        assertThat(reconciliation.reconcile()).isEmpty();
    }
}
