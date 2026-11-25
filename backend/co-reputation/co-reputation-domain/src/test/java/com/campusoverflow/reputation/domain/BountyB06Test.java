package com.campusoverflow.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** B06 悬赏：来源授权规则、新 open 重载、争议标记、旧签名兼容。 */
class BountyB06Test {

    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");
    private final Actor teacher = new Actor(9, "王老师", Role.TEACHER);

    @Test
    void personalSourceAllowsQuestionAuthorOrCourseManager() {
        assertThat(BountySource.PERSONAL.allows(1, 1, false, 1L)).isTrue();
        assertThat(BountySource.PERSONAL.allows(9, 1, true, 1L)).isTrue();
        assertThat(BountySource.PERSONAL.allows(9, 1, false, 1L)).isFalse();
    }

    @Test
    void courseBudgetSourceRequiresManagerAndCourse() {
        assertThat(BountySource.COURSE_BUDGET.allows(9, 1, true, 1L)).isTrue();
        assertThat(BountySource.COURSE_BUDGET.allows(9, 1, true, null)).isFalse();
        assertThat(BountySource.COURSE_BUDGET.allows(1, 1, false, 1L)).isFalse();
    }

    @Test
    void openWithSourceKeepsAllFields() {
        Bounty bounty = Bounty.open(10, true, teacher, true, 50, 7, NOW,
                BountySource.COURSE_BUDGET, 3L, "bounty:open:cmd-1");

        assertThat(bounty.source()).isEqualTo(BountySource.COURSE_BUDGET);
        assertThat(bounty.sourceId()).isEqualTo(3L);
        assertThat(bounty.commandKey()).isEqualTo("bounty:open:cmd-1");
        assertThat(bounty.disputed()).isFalse();
        assertThat(bounty.status()).isEqualTo(BountyStatus.OPEN);
        assertThat(bounty.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void openRejectsUnauthorizedAndBounds() {
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, false, 50, 7, NOW,
                BountySource.PERSONAL, 9, null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, true, 5, 7, NOW,
                BountySource.PERSONAL, 9, null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, true, 501, 7, NOW,
                BountySource.PERSONAL, 9, null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, true, 50, 0, NOW,
                BountySource.PERSONAL, 9, null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, true, 50, 15, NOW,
                BountySource.PERSONAL, 9, null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Bounty.open(10, false, teacher, true, 50, 7, NOW,
                BountySource.PERSONAL, 9, null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void markDisputedOnlyForAwardedAndIsIdempotent() {
        Bounty bounty = Bounty.open(10, true, teacher, true, 50, 7, NOW,
                BountySource.PERSONAL, 9, null);

        assertThatThrownBy(bounty::markDisputed).isInstanceOf(ConflictException.class);

        bounty.award(50, 3, NOW);
        bounty.markDisputed();
        bounty.markDisputed();

        assertThat(bounty.disputed()).isTrue();
        assertThat(bounty.status()).isEqualTo(BountyStatus.AWARDED);
    }

    @Test
    void legacyOpenSignatureDefaultsToPersonal() {
        Bounty bounty = Bounty.open(10, true, teacher, true, 50, 7, NOW);

        assertThat(bounty.source()).isEqualTo(BountySource.PERSONAL);
        assertThat(bounty.sourceId()).isEqualTo(9L);
        assertThat(bounty.commandKey()).isNull();
        assertThat(bounty.disputed()).isFalse();
    }

    @Test
    void reconstituteCarriesB06Fields() {
        Bounty bounty = Bounty.reconstitute(5L, 10, 9, 50, BountySource.COURSE_BUDGET, 3L, "cmd",
                BountyStatus.AWARDED, NOW, NOW.plus(Duration.ofDays(7)), 3L, 50L, NOW, true);

        assertThat(bounty.id()).isEqualTo(5L);
        assertThat(bounty.source()).isEqualTo(BountySource.COURSE_BUDGET);
        assertThat(bounty.sourceId()).isEqualTo(3L);
        assertThat(bounty.commandKey()).isEqualTo("cmd");
        assertThat(bounty.winnerId()).isEqualTo(3L);
        assertThat(bounty.answerId()).isEqualTo(50L);
        assertThat(bounty.closedAt()).isEqualTo(NOW);
        assertThat(bounty.disputed()).isTrue();
        assertThat(bounty.isDue(NOW.plus(Duration.ofDays(8)))).isFalse();
    }

    @Test
    void legacyReconstituteDefaultsToPersonal() {
        Bounty bounty = Bounty.reconstitute(5L, 10, 9, 50, BountyStatus.OPEN, NOW,
                NOW.plus(Duration.ofDays(7)), null, null, null);

        assertThat(bounty.source()).isEqualTo(BountySource.PERSONAL);
        assertThat(bounty.sourceId()).isEqualTo(9L);
        assertThat(bounty.disputed()).isFalse();
        assertThat(bounty.isDue(NOW.plus(Duration.ofDays(7)))).isTrue();
    }

    @Test
    void assigningIdTwiceIsRejected() {
        Bounty bounty = Bounty.open(10, true, teacher, true, 50, 7, NOW,
                BountySource.PERSONAL, 9, null);
        bounty.assignId(1);

        assertThatThrownBy(() -> bounty.assignId(2)).isInstanceOf(IllegalStateException.class);
    }
}
