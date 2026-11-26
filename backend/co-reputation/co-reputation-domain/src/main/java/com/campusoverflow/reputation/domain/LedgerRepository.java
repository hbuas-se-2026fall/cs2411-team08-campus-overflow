package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface LedgerRepository {
    boolean exists(String eventId, long userId, ReputationReason reason);

    void append(LedgerEntry entry);

    PageResult<LedgerEntry> findByUser(long userId, PageRequest page);

    /** 声誉流水分页：只返回 REPUTATION 币种事由，供 /reputation/ledger 使用。 */
    PageResult<LedgerEntry> findReputationByUser(long userId, PageRequest page);

    /** 积分流水分页：只返回 POINTS 币种事由。 */
    PageResult<LedgerEntry> findPointsByUser(long userId, PageRequest page);

    /**
     * 冲正时定位原流水：该用户、该事由、同一引用下，尚未被冲正过的正向流水中最新的一条。
     * 找不到（例如自问自答 delta=0 从未记账）时返回空，调用方不产生冲正流水。
     */
    Optional<LedgerEntry> findReversible(long userId, ReputationReason reason, String refType, long refId);

    /** 防止不同撤销事件对同一条原流水重复冲正。 */
    boolean existsReversalOf(String originalEventId, long userId, ReputationReason reason);

    long countByUserAndReason(long userId, ReputationReason reason);

    /** 某用户自 since 起因被投票获得的正向声誉总和（用于每日上限）。 */
    int sumPositiveVoteDeltaSince(long userId, Instant since);

    /** 对账：每个用户的 REPUTATION 币种流水总和（POINTS 币种不参与 FF-8）。 */
    Map<Long, Long> sumByUser();
}
