package com.campusoverflow.qa.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EndorsementLogJpaRepository extends JpaRepository<EndorsementLogJpaEntity, Long> {

    List<EndorsementLogJpaEntity> findByAnswerIdOrderByIdAsc(long answerId);
}
