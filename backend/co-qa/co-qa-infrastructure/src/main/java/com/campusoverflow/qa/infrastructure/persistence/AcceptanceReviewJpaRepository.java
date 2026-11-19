package com.campusoverflow.qa.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcceptanceReviewJpaRepository extends JpaRepository<AcceptanceReviewJpaEntity, Long> {

    List<AcceptanceReviewJpaEntity> findByQuestionIdOrderByIdAsc(long questionId);
}
