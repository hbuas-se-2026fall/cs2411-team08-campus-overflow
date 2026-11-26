package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.reputation.application.fake.ReputationFixtures;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryBudgets;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryIdempotency;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.MutableClock;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.RecordingAudit;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.StubIdentity;
import com.campusoverflow.reputation.domain.BudgetAdjustmentSource;
import com.campusoverflow.reputation.domain.CourseBudget;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** B06 课程预算应用层：管理员分配留痕、幂等、越权与不存在课程。 */
class CourseBudgetServiceTest {

    private final InMemoryBudgets budgets = new InMemoryBudgets();
    private final InMemoryIdempotency idempotency = new InMemoryIdempotency();
    private final RecordingAudit audit = new RecordingAudit();
    private final MutableClock clock = new MutableClock(ReputationFixtures.NOW, ZoneOffset.UTC);
    private final StubIdentity identity = new StubIdentity();
    private final CourseBudgetService service = new CourseBudgetService(budgets, identity, idempotency, audit, clock);

    private final Actor admin = new Actor(99, "管理员", Role.ADMIN);
    private final Actor student = new Actor(1, "小明", Role.STUDENT);

    @BeforeEach
    void setUp() {
        CourseBudget budget = CourseBudget.open(1);
        budgets.save(budget);
    }

    @Test
    void adminAdjustmentRecordsSourceOperatorAndReason() {
        BudgetView view = service.adjust(admin, 1, 500, "期初分配", "cmd-1");

        assertThat(view.availablePoints()).isEqualTo(500);
        assertThat(view.totalPoints()).isEqualTo(500);
        assertThat(budgets.adjustments).hasSize(1).allSatisfy(a -> {
            assertThat(a.delta()).isEqualTo(500);
            assertThat(a.source()).isEqualTo(BudgetAdjustmentSource.ADMIN_ALLOCATION);
            assertThat(a.operatorId()).isEqualTo(99);
            assertThat(a.reason()).isEqualTo("期初分配");
        });
        assertThat(audit.actions).contains("BUDGET_ADJUSTED");
    }

    @Test
    void adjustmentIsIdempotent() {
        service.adjust(admin, 1, 500, "期初分配", "cmd-2");
        BudgetView replay = service.adjust(admin, 1, 500, "期初分配", "cmd-2");

        assertThat(replay.availablePoints()).isEqualTo(500);
        assertThat(budgets.adjustments).hasSize(1);
    }

    @Test
    void budgetViewShowsConservationAndRecentAdjustments() {
        service.adjust(admin, 1, 500, "期初分配", "cmd-3");
        budgets.get(1).freezePoints(50);

        BudgetView view = service.budget(1, admin);

        assertThat(view.availablePoints()).isEqualTo(450);
        assertThat(view.frozenPoints()).isEqualTo(50);
        assertThat(view.totalPoints()).isEqualTo(500);
        assertThat(view.recentAdjustments()).hasSize(1);
        assertThat(view.courseName()).isNotBlank();
    }

    @Test
    void nonAdminCannotAdjust() {
        assertThatThrownBy(() -> service.adjust(student, 1, 100, "越权", "cmd-4"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void zeroDeltaAndBlankReasonAreRejected() {
        assertThatThrownBy(() -> service.adjust(admin, 1, 0, "零", "cmd-5"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.adjust(admin, 1, 100, "  ", "cmd-6"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void overdraftIsRejected() {
        assertThatThrownBy(() -> service.adjust(admin, 1, -1, "回收过多", "cmd-7"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void unknownCourseIsRejected() {
        IdentityApi noCourse = new StubIdentity() {
            @Override
            public Optional<CourseSummary> findCourse(long courseId) {
                return Optional.empty();
            }
        };
        CourseBudgetService unknown = new CourseBudgetService(budgets, noCourse, idempotency, audit, clock);

        assertThatThrownBy(() -> unknown.budget(42, admin)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> unknown.adjust(admin, 42, 100, "分配", "cmd-8"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void readRequiresCourseMembership() {
        IdentityApi outsider = new StubIdentity() {
            @Override
            public boolean canManageCourse(long userId, Long courseId) {
                return false;
            }

            @Override
            public boolean isCourseMember(long userId, long courseId) {
                return false;
            }
        };
        CourseBudgetService restricted = new CourseBudgetService(budgets, outsider, idempotency, audit, clock);

        assertThatThrownBy(() -> restricted.budget(1, student)).isInstanceOf(ForbiddenException.class);
        assertThat(restricted.budget(1, admin).courseId()).isEqualTo(1);
    }
}
