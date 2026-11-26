package com.campusoverflow.reputation.infrastructure.persistence;

import com.campusoverflow.reputation.domain.PointsRecovery;
import com.campusoverflow.reputation.domain.PointsRecoveryRepository;
import com.campusoverflow.reputation.domain.PointsRecoveryStatus;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PointsRecoveryPersistenceAdapter implements PointsRecoveryRepository {

    private final PointsRecoveryJpaRepository recoveries;

    public PointsRecoveryPersistenceAdapter(PointsRecoveryJpaRepository recoveries) {
        this.recoveries = recoveries;
    }

    @Override
    public Optional<PointsRecovery> findPendingByUser(long userId) {
        return recoveries.findFirstByUserIdAndStatus(userId, PointsRecoveryStatus.PENDING.name())
                .map(PointsRecoveryPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<PointsRecovery> findById(long id) {
        return recoveries.findById(id).map(PointsRecoveryPersistenceAdapter::toDomain);
    }

    @Override
    public PointsRecovery save(PointsRecovery recovery) {
        PointsRecoveryJpaEntity e = recovery.id() == null
                ? new PointsRecoveryJpaEntity(recovery.userId(), recovery.amount(), recovery.status().name(),
                        recovery.sourceEventId(), recovery.createdAt())
                : recoveries.findById(recovery.id()).orElseThrow();
        e.setAmount(recovery.amount());
        e.setStatus(recovery.status().name());
        e.setResolvedAt(recovery.resolvedAt());
        e.setResolvedBy(recovery.resolvedBy());
        e.setNote(recovery.note());
        e.setPendingSlot(recovery.status() == PointsRecoveryStatus.PENDING ? recovery.userId() : null);
        PointsRecoveryJpaEntity saved = recoveries.saveAndFlush(e);
        if (recovery.id() == null) {
            recovery.assignId(saved.getId());
        }
        return recovery;
    }

    @Override
    public PageResult<PointsRecovery> findAll(com.campusoverflow.shared.page.PageRequest page) {
        List<PointsRecovery> items = recoveries.findAllByOrderByIdDesc(
                        org.springframework.data.domain.PageRequest.of(page.page() - 1, page.size())).stream()
                .map(PointsRecoveryPersistenceAdapter::toDomain).toList();
        return new PageResult<>(items, recoveries.count(), page.page(), page.size());
    }

    private static PointsRecovery toDomain(PointsRecoveryJpaEntity e) {
        return PointsRecovery.reconstitute(e.getId(), e.getUserId(), e.getAmount(),
                PointsRecoveryStatus.valueOf(e.getStatus()), e.getSourceEventId(), e.getCreatedAt(),
                e.getResolvedAt(), e.getResolvedBy(), e.getNote());
    }
}
