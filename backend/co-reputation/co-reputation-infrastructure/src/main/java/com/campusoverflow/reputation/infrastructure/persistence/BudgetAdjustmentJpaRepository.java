package com.campusoverflow.reputation.infrastructure.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetAdjustmentJpaRepository extends JpaRepository<BudgetAdjustmentJpaEntity, Long> {

    List<BudgetAdjustmentJpaEntity> findByCourseIdOrderByIdDesc(long courseId, Pageable pageable);

    long countByCourseId(long courseId);

    boolean existsByEventIdAndCourseId(String eventId, long courseId);
}
