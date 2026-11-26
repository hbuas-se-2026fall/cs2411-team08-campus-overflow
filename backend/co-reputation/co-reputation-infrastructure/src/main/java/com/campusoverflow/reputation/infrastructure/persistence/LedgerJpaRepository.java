package com.campusoverflow.reputation.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerJpaRepository extends JpaRepository<LedgerJpaEntity, Long> {

    boolean existsByEventIdAndUserIdAndReason(String eventId, long userId, String reason);

    boolean existsByReversesEventIdAndUserIdAndReason(String reversesEventId, long userId, String reason);

    Page<LedgerJpaEntity> findByUserIdOrderByIdDesc(long userId, Pageable pageable);

    Page<LedgerJpaEntity> findByUserIdAndReasonInOrderByIdDesc(long userId, Collection<String> reasons,
                                                               Pageable pageable);

    /** 徽章计数只统计「正向且非冲正」的流水，避免撤销后重新采纳虚增计数。 */
    @Query("""
            select count(l) from LedgerJpaEntity l
            where l.userId = :userId and l.reason = :reason and l.delta > 0 and l.reversesEventId is null
            """)
    long countPositiveByUserIdAndReason(@Param("userId") long userId, @Param("reason") String reason);

    @Query("""
            select l from LedgerJpaEntity l
            where l.userId = :userId and l.reason = :reason and l.refType = :refType and l.refId = :refId
              and l.delta > 0 and l.reversesEventId is null
            order by l.id desc
            """)
    List<LedgerJpaEntity> findReversible(@Param("userId") long userId, @Param("reason") String reason,
                                         @Param("refType") String refType, @Param("refId") long refId,
                                         Pageable pageable);

    default Optional<LedgerJpaEntity> findLatestReversible(long userId, String reason, String refType, long refId) {
        return findReversible(userId, reason, refType, refId, Pageable.ofSize(1)).stream().findFirst();
    }

    @Query("""
            select coalesce(sum(l.delta), 0) from LedgerJpaEntity l
            where l.userId = :userId and l.delta > 0 and l.reason in :reasons and l.createdAt >= :since
            """)
    Long sumPositiveSince(@Param("userId") long userId, @Param("reasons") Collection<String> reasons,
                          @Param("since") Instant since);

    /** FF-8 对账只统计 REPUTATION 币种事由。 */
    @Query("""
            select l.userId, sum(l.delta) from LedgerJpaEntity l
            where l.reason in :reasons group by l.userId
            """)
    List<Object[]> sumGroupByUser(@Param("reasons") Collection<String> reasons);
}
