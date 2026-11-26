package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyClosedEvent;
import com.campusoverflow.reputation.api.event.BountyOpenedEvent;
import com.campusoverflow.reputation.application.fake.ReputationFixtures;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryAccounts;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryBadges;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryBounties;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryBudgets;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryIdempotency;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryLedger;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.MutableClock;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.RecordingAudit;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.RecordingEvents;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.StubIdentity;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.StubQa;
import com.campusoverflow.reputation.domain.BountySource;
import com.campusoverflow.reputation.domain.BudgetAdjustmentSource;
import com.campusoverflow.reputation.domain.CourseBudget;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** B06 悬赏应用层：两来源分账、幂等、并发终态、退款、争议。 */
class BountyB06ServiceTest {

    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final InMemoryLedger ledger = new InMemoryLedger();
    private final InMemoryBadges badges = new InMemoryBadges();
    private final RecordingEvents events = new RecordingEvents();
    private final StubIdentity identity = new StubIdentity();
    private final StubQa qa = new StubQa();
    private final RecordingAudit audit = new RecordingAudit();
    private final MutableClock clock = new MutableClock(ReputationFixtures.NOW, ZoneOffset.UTC);
    private final InMemoryBounties bounties = new InMemoryBounties();
    private final InMemoryBudgets budgets = new InMemoryBudgets();
    private final InMemoryIdempotency idempotency = new InMemoryIdempotency();

    private final BadgeService badgeService = new BadgeService(badges, events, clock);
    private final ReputationLedgerService ledgerService = new ReputationLedgerService(accounts, ledger, badgeService,
            events, clock);
    private final BountyFundingRegistry registry = new BountyFundingRegistry(List.of(
            new PersonalPointsFunding(accounts), new CourseBudgetFunding(budgets)));
    private final BountyService service = new BountyService(bounties, ledgerService, qa, identity, events, audit,
            clock, registry, idempotency);

    private final Actor teacher = new Actor(9, "王老师", Role.TEACHER);
    private final Actor student = new Actor(1, "小明", Role.STUDENT);

    @BeforeEach
    void setUp() {
        accounts.save(ReputationAccount.open(9, 1000));
        accounts.save(ReputationAccount.open(1, 100));
        accounts.save(ReputationAccount.open(3, 0));
        identity.user(9, "王老师", Role.TEACHER).manager(9).user(1, "小明", Role.STUDENT)
                .user(3, "小红", Role.STUDENT);
        qa.question(10, 1, 1L, null, false);
        CourseBudget budget = CourseBudget.open(1);
        budget.adjust(500);
        budgets.save(budget);
    }

    @Test
    void studentCanOpenPersonalBountyOnOwnQuestion() {
        long id = service.open(student, 10, 50, 7, "cmd-1", BountySource.PERSONAL, null);

        assertThat(id).isPositive();
        assertThat(accounts.get(1).availablePoints()).isEqualTo(50);
        assertThat(accounts.get(1).frozenPoints()).isEqualTo(50);
        assertThat(budgets.get(1).frozenPoints()).isZero();
        assertThat(events.ofType(BountyOpenedEvent.class)).hasSize(1);
        assertThat(bounties.get(id).source()).isEqualTo(BountySource.PERSONAL);
    }

    @Test
    void courseBudgetBountyFreezesCourseNotSponsor() {
        long id = service.open(teacher, 10, 50, 7, "cmd-2", BountySource.COURSE_BUDGET, 1L);

        assertThat(budgets.get(1).frozenPoints()).isEqualTo(50);
        assertThat(budgets.get(1).availablePoints()).isEqualTo(450);
        assertThat(accounts.get(9).frozenPoints()).isZero();
        assertThat(bounties.get(id).source()).isEqualTo(BountySource.COURSE_BUDGET);
        assertThat(bounties.get(id).sourceId()).isEqualTo(1L);
    }

    @Test
    void idempotentReplayReturnsSameBountyId() {
        long first = service.open(teacher, 10, 50, 7, "cmd-3", BountySource.PERSONAL, null);
        long second = service.open(teacher, 10, 50, 7, "cmd-3", BountySource.PERSONAL, null);

        assertThat(second).isEqualTo(first);
        assertThat(accounts.get(9).frozenPoints()).isEqualTo(50);
        assertThat(events.ofType(BountyOpenedEvent.class)).hasSize(1);
    }

    @Test
    void inProgressCommandIsRejected() {
        idempotency.markRunning("bounty:open:cmd-4", ReputationFixtures.NOW);

        assertThatThrownBy(() -> service.open(teacher, 10, 50, 7, "cmd-4", BountySource.PERSONAL, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void secondOpenBountyOnSameQuestionIsRejected() {
        service.open(teacher, 10, 50, 7, "cmd-5", BountySource.PERSONAL, null);

        assertThatThrownBy(() -> service.open(teacher, 10, 30, 3, "cmd-6", BountySource.PERSONAL, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void courseMismatchIsRejected() {
        assertThatThrownBy(() -> service.open(teacher, 10, 50, 7, "cmd-7", BountySource.COURSE_BUDGET, 2L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void nonManagerCannotUseCourseBudget() {
        assertThatThrownBy(() -> service.open(student, 10, 50, 7, "cmd-8", BountySource.COURSE_BUDGET, 1L))
                .isInstanceOf(com.campusoverflow.shared.domain.ForbiddenException.class);
    }

    @Test
    void budgetBountyExpiryRefundsCourseAndRecordsAdjustment() {
        service.open(teacher, 10, 50, 7, "cmd-9", BountySource.COURSE_BUDGET, 1L);
        clock.advance(Duration.ofDays(8));

        assertThat(service.expireDue()).isEqualTo(1);

        assertThat(budgets.get(1).frozenPoints()).isZero();
        assertThat(budgets.get(1).availablePoints()).isEqualTo(500);
        assertThat(budgets.adjustments).hasSize(1)
                .allSatisfy(a -> {
                    assertThat(a.source()).isEqualTo(BudgetAdjustmentSource.BOUNTY_REFUND);
                    assertThat(a.delta()).isEqualTo(50);
                });
        assertThat(events.ofType(BountyClosedEvent.class)).hasSize(1);
    }

    @Test
    void budgetBountyAwardSpendsCourseAndAwardsReputation() {
        service.open(teacher, 10, 50, 7, "cmd-10", BountySource.COURSE_BUDGET, 1L);

        service.settleOnAccept(10, 50, 3, "accept-1");

        assertThat(budgets.get(1).frozenPoints()).isZero();
        assertThat(budgets.get(1).availablePoints()).isEqualTo(450);
        assertThat(accounts.get(3).reputation()).isEqualTo(50);
        assertThat(events.ofType(BountyAwardedEvent.class)).hasSize(1);
        assertThat(budgets.adjustments).hasSize(1)
                .allSatisfy(a -> assertThat(a.source()).isEqualTo(BudgetAdjustmentSource.BOUNTY_SPEND));
    }

    @Test
    void concurrentSettlementAppliesSideEffectsOnlyOnce() {
        long id = service.open(teacher, 10, 50, 7, "cmd-11", BountySource.PERSONAL, null);
        bounties.tryAward(id, 50, 3, ReputationFixtures.NOW);

        service.settleOnAccept(10, 50, 3, "accept-2");

        assertThat(accounts.get(3).reputation()).isZero();
        assertThat(accounts.get(9).frozenPoints()).isEqualTo(50);
        assertThat(events.ofType(BountyAwardedEvent.class)).isEmpty();
    }

    @Test
    void questionDeletionRefundsAndIsIdempotent() {
        service.open(teacher, 10, 50, 7, "cmd-12", BountySource.PERSONAL, null);

        service.refundOnQuestionDeleted(10, "del-1");
        service.refundOnQuestionDeleted(10, "del-1");

        assertThat(accounts.get(9).availablePoints()).isEqualTo(1000);
        assertThat(accounts.get(9).frozenPoints()).isZero();
        assertThat(events.ofType(BountyClosedEvent.class)).hasSize(1);
    }

    @Test
    void flagDisputedOnlyOnceAndDoesNotRefund() {
        service.open(teacher, 10, 50, 7, "cmd-13", BountySource.PERSONAL, null);
        service.settleOnAccept(10, 50, 3, "accept-3");

        service.flagDisputed(10, 3, "unaccept-1");
        service.flagDisputed(10, 3, "unaccept-1");

        assertThat(bounties.get(1L).disputed()).isTrue();
        assertThat(accounts.get(9).frozenPoints()).isZero();
        assertThat(accounts.get(9).availablePoints()).isEqualTo(950);
        assertThat(audit.actions.stream().filter("BOUNTY_AWARD_DISPUTED"::equals).count()).isEqualTo(1);
    }

    @Test
    void latestExposesSourceAndDisputeFlag() {
        long id = service.open(teacher, 10, 50, 7, "cmd-14", BountySource.COURSE_BUDGET, 1L);

        var view = service.latest(10).orElseThrow();

        assertThat(view.id()).isEqualTo(id);
        assertThat(view.source()).isEqualTo("COURSE_BUDGET");
        assertThat(view.courseId()).isEqualTo(1L);
        assertThat(view.disputed()).isFalse();
    }
}
