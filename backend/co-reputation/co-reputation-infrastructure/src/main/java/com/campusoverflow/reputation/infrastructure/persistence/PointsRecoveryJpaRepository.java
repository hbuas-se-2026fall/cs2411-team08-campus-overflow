package com.campusoverflow.reputation.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointsRecoveryJpaRepository extends JpaRepository<PointsRecoveryJpaEntity, Long> {

    Optional<PointsRecoveryJpaEntity> findFirstByUserIdAndStatus(long userId, String status);

    List<PointsRecoveryJpaEntity> findAllByOrderByIdDesc(Pageable pageable);

    long count();
}
