package com.campusoverflow.reputation.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BountyJpaRepository extends JpaRepository<BountyJpaEntity, Long> {

    Optional<BountyJpaEntity> findFirstByQuestionIdAndStatus(long questionId, String status);

    Optional<BountyJpaEntity> findFirstByQuestionIdOrderByIdDesc(long questionId);

    List<BountyJpaEntity> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAt(String status, Instant now,
                                                                                 Pageable pageable);

    /** 条件更新抢终态：WHERE status='OPEN' 保证并发下只有一个调用方能成功。 */
    @Modifying
    @Query("""
            UPDATE BountyJpaEntity b SET b.status = 'AWARDED', b.winnerId = :winnerId,
                b.answerId = :answerId, b.closedAt = :now, b.openSlot = NULL
            WHERE b.id = :id AND b.status = 'OPEN'
            """)
    int awardIfOpen(@Param("id") long id, @Param("answerId") long answerId,
                    @Param("winnerId") long winnerId, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE BountyJpaEntity b SET b.status = 'EXPIRED', b.closedAt = :now, b.openSlot = NULL
            WHERE b.id = :id AND b.status = 'OPEN'
            """)
    int expireIfOpen(@Param("id") long id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE BountyJpaEntity b SET b.disputed = TRUE WHERE b.id = :id AND b.status = 'AWARDED'")
    int markDisputed(@Param("id") long id);
}
