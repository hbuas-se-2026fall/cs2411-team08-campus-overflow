package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.reputation.application.fake.ReputationFixtures;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryAccounts;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.InMemoryRecoveries;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.MutableClock;
import com.campusoverflow.reputation.application.fake.ReputationFixtures.RecordingAudit;
import com.campusoverflow.reputation.domain.PointsRecovery;
import com.campusoverflow.reputation.domain.PointsRecoveryStatus;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** B06 待追回应用层：幂等登记、实时缺口、复核（不自动扣款）。 */
class PointsRecoveryServiceTest {

    private final InMemoryRecoveries recoveries = new InMemoryRecoveries();
    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final RecordingAudit audit = new RecordingAudit();
    private final MutableClock clock = new MutableClock(ReputationFixtures.NOW, ZoneOffset.UTC);
    private final PointsRecoveryService service = new PointsRecoveryService(recoveries, accounts, audit, clock);

    private final Actor admin = new Actor(99, "管理员", Role.ADMIN);
    private final Actor student = new Actor(2, "小红", Role.STUDENT);

    @Test
    void ensurePendingIsIdempotentAndRaisesAmount() {
        service.ensurePending(2, 5, "revoke-1");
        service.ensurePending(2, 3, "revoke-1");
        service.ensurePending(2, 9, "revoke-1");

        assertThat(recoveries.findPendingByUser(2)).isPresent()
                .get().extracting(PointsRecovery::amount).isEqualTo(9);
        assertThat(recoveries.findAll(new PageRequest(1, 20)).items()).hasSize(1);
    }

    @Test
    void zeroAmountIsIgnored() {
        service.ensurePending(2, 0, "revoke-1");

        assertThat(recoveries.findPendingByUser(2)).isEmpty();
    }

    @Test
    void pendingShowsCurrentGapFromAccount() {
        accounts.save(ReputationAccount.open(2, 0));
        accounts.get(2).applyPoints(-5);
        service.ensurePending(2, 5, "revoke-1");

        var page = service.pending(new PageRequest(1, 20));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).currentGap()).isEqualTo(5);
        assertThat(page.items().get(0).status()).isEqualTo("PENDING");
    }

    @Test
    void adminResolveMarksDecisionWithoutChangingBalance() {
        accounts.save(ReputationAccount.open(2, 0));
        service.ensurePending(2, 5, "revoke-1");
        long id = recoveries.findPendingByUser(2).orElseThrow().id();

        var view = service.resolve(id, admin, PointsRecovery.Decision.RECOVERED, "已线下追回");

        assertThat(view.status()).isEqualTo(PointsRecoveryStatus.RESOLVED.name());
        assertThat(view.resolvedBy()).isEqualTo(99);
        assertThat(accounts.get(2).availablePoints()).isZero();
        assertThat(audit.actions).contains("POINTS_RECOVERY_RESOLVED");
    }

    @Test
    void waiverRecordsWaivedStatus() {
        service.ensurePending(2, 5, "revoke-1");
        long id = recoveries.findPendingByUser(2).orElseThrow().id();

        var view = service.resolve(id, admin, PointsRecovery.Decision.WAIVED, "复核后豁免");

        assertThat(view.status()).isEqualTo(PointsRecoveryStatus.WAIVED.name());
        assertThat(view.note()).isEqualTo("复核后豁免");
    }

    @Test
    void nonAdminAndMissingRecordAreRejected() {
        service.ensurePending(2, 5, "revoke-1");
        long id = recoveries.findPendingByUser(2).orElseThrow().id();

        assertThatThrownBy(() -> service.resolve(id, student, PointsRecovery.Decision.WAIVED, "越权"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.resolve(999, admin, PointsRecovery.Decision.WAIVED, "不存在"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void secondSettleIsRejected() {
        service.ensurePending(2, 5, "revoke-1");
        long id = recoveries.findPendingByUser(2).orElseThrow().id();
        service.resolve(id, admin, PointsRecovery.Decision.RECOVERED, "已追回");

        assertThatThrownBy(() -> service.resolve(id, admin, PointsRecovery.Decision.WAIVED, "重复"))
                .isInstanceOf(com.campusoverflow.shared.domain.ConflictException.class);
    }
}
