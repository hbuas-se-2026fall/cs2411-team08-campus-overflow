package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.identity.api.event.UserRegisteredEvent;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.VoteCastEvent;
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
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ReputationCommandServiceTest {

    private static final Instant NOW = ReputationFixtures.NOW;

    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final InMemoryLedger ledger = new InMemoryLedger();
    private final InMemoryBadges badges = new InMemoryBadges();
    private final RecordingEvents events = new RecordingEvents();
    private final StubIdentity identity = new StubIdentity();
    private final StubQa qa = new StubQa();
    private final RecordingAudit audit = new RecordingAudit();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final BadgeService badgeService = new BadgeService(badges, events, clock);
    private final ReputationLedgerService ledgerService = new ReputationLedgerService(accounts, ledger, badgeService,
            events, clock);
    private final BountyService bountyService = new BountyService(new InMemoryBounties(), accounts, ledgerService, qa,
            identity, events, audit, clock);
    private final ReputationEventHandlers handlers = new ReputationEventHandlers(accounts, ledger, ledgerService,
            badgeService, bountyService, clock);

    @Test
    void acceptingAnswerCreditsFifteenAndGrantsBadge() {
        accounts.save(ReputationAccount.open(2, 0));
        handlers.on(new AnswerAcceptedEvent("e1", NOW, 50, 10, 2, 1, 1, "标题"));

        assertThat(accounts.get(2).reputation()).isEqualTo(15);
        assertThat(badges.has(2, BadgeType.FIRST_ACCEPTED)).isTrue();
        assertThat(ledger.entries).hasSize(1);
    }

    @Test
    void replayingSameEventIsIdempotent() {
        accounts.save(ReputationAccount.open(2, 0));
        AnswerAcceptedEvent event = new AnswerAcceptedEvent("e1", NOW, 50, 10, 2, 1, 1, "标题");

        handlers.on(event);
        handlers.on(event);

        assertThat(accounts.get(2).reputation()).isEqualTo(15);
        assertThat(ledger.entries).hasSize(1);
    }

    @Test
    void selfAcceptedAnswerEarnsNothing() {
        accounts.save(ReputationAccount.open(2, 0));
        handlers.on(new AnswerAcceptedEvent("e1", NOW, 50, 10, 2, 2, 2, "标题"));

        assertThat(accounts.get(2).reputation()).isZero();
        assertThat(ledger.entries).isEmpty();
    }

    @Test
    void positiveVoteGainIsCappedAtDailyLimit() {
        accounts.save(ReputationAccount.reconstitute(2, 195, 0, 0, 0));
        ledger.append(LedgerEntry.of(2, "old", ReputationReason.ANSWER_VOTE, 195, "ANSWER", 1,
                NOW.minusSeconds(60)));

        handlers.on(new VoteCastEvent("v1", NOW, "ANSWER", 50, 2, 1, 10, 0, 1));

        assertThat(accounts.get(2).reputation()).isEqualTo(200);
        assertThat(ledger.entries).hasSize(2);
    }

    @Test
    void negativeVoteIgnoresDailyCap() {
        accounts.save(ReputationAccount.open(2, 0));
        handlers.on(new VoteCastEvent("v1", NOW, "QUESTION", 10, 2, 1, 10, 0, -1));

        assertThat(accounts.get(2).reputation()).isEqualTo(-2);
    }

    @Test
    void teacherRegistrationOpensAccountWithInitialPoints() {
        handlers.on(new UserRegisteredEvent("u1", NOW, 9, "王老师", Role.TEACHER));

        assertThat(accounts.get(9).availablePoints()).isEqualTo(1000);
        assertThat(accounts.get(9).reputation()).isZero();
    }

    @Test
    void firstAnswerGrantsBadge() {
        handlers.on(new AnswerSubmittedEvent("a1", NOW, 50, 10, 2, 1, "标题"));

        assertThat(badges.has(2, BadgeType.FIRST_ANSWER)).isTrue();
    }
}
