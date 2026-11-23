package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyClosedEvent;
import com.campusoverflow.reputation.api.event.BountyOpenedEvent;
import com.campusoverflow.reputation.application.fake.ReputationFixtures;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryAccounts;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryBadges;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryBounties;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryLedger;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.MutableClock;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.RecordingAudit;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.RecordingEvents;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.StubIdentity;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.StubQa;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Duration;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BountyServiceTest {

    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final InMemoryLedger ledger = new InMemoryLedger();
    private final InMemoryBadges badges = new InMemoryBadges();
    private final RecordingEvents events = new RecordingEvents();
    private final StubIdentity identity = new StubIdentity();
    private final StubQa qa = new StubQa();
    private final RecordingAudit audit = new RecordingAudit();
    private final MutableClock clock = new MutableClock(ReputationFixtures.NOW, ZoneOffset.UTC);

    private final BadgeService badgeService = new BadgeService(badges, events, clock);
    private final ReputationLedgerService ledgerService = new ReputationLedgerService(accounts, ledger, badgeService,
            events, clock);
    private final InMemoryBounties bounties = new InMemoryBounties();
    private final BountyService service = new BountyService(bounties, accounts, ledgerService, qa, identity, events,
            audit, clock);

    private final Actor teacher = new Actor(9, "王老师", Role.TEACHER);

    @BeforeEach
    void setUp() {
        accounts.save(ReputationAccount.open(9, 1000));
        identity.user(9, "王老师", Role.TEACHER).manager(9).user(3, "小红", Role.STUDENT);
        qa.question(10, 1, 1L, null, false);
    }

    @Test
    void openFreezesPointsAndPublishesEvent() {
        long id = service.open(teacher, 10, 50, 7);

        assertThat(id).isPositive();
        assertThat(accounts.get(9).availablePoints()).isEqualTo(950);
        assertThat(accounts.get(9).frozenPoints()).isEqualTo(50);
        assertThat(events.ofType(BountyOpenedEvent.class)).hasSize(1);
    }

    @Test
    void settleOnAcceptAwardsWinnerAndSpendsFrozen() {
        service.open(teacher, 10, 50, 7);
        accounts.save(ReputationAccount.open(3, 0));

        service.settleOnAccept(10, 50, 3, "accept-1");

        assertThat(accounts.get(9).frozenPoints()).isZero();
        assertThat(accounts.get(3).reputation()).isEqualTo(50);
        assertThat(events.ofType(BountyAwardedEvent.class)).hasSize(1);
    }

    @Test
    void expireDueRefundsSponsor() {
        service.open(teacher, 10, 50, 1);
        clock.advance(Duration.ofDays(2));

        int closed = service.expireDue();

        assertThat(closed).isEqualTo(1);
        assertThat(accounts.get(9).availablePoints()).isEqualTo(1000);
        assertThat(accounts.get(9).frozenPoints()).isZero();
        assertThat(events.ofType(BountyClosedEvent.class)).hasSize(1);
    }

    @Test
    void sponsorWinningOwnBountyClosesAndRefunds() {
        service.open(teacher, 10, 50, 7);

        service.settleOnAccept(10, 50, 9, "accept-2");

        assertThat(accounts.get(9).frozenPoints()).isZero();
        assertThat(accounts.get(9).availablePoints()).isEqualTo(1000);
        assertThat(events.ofType(BountyClosedEvent.class)).hasSize(1);
    }
}
