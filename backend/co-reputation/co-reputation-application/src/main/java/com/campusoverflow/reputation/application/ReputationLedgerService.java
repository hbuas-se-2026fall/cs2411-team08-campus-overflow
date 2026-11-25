package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.api.event.ReputationChangedEvent;
import com.campusoverflow.reputation.domain.BadgePolicy;
import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.reputation.domain.ReputationRulebook;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 声誉记账核心：先查重（幂等）→ 改余额 → 追加流水 → 发布 ReputationChanged。
 * 余额与流水在同一事务中提交，保证“余额 = 流水之和”。
 * 声誉与积分共用账本，但按 ReputationReason 的币种分别改余额；只有声誉流水会发集成事件、
 * 参与 FF-8 对账，积分流水不会污染排行榜与搜索投影。
 */
@Service
public class ReputationLedgerService {

    private final ReputationAccountRepository accounts;
    private final LedgerRepository ledger;
    private final BadgeService badges;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public ReputationLedgerService(ReputationAccountRepository accounts, LedgerRepository ledger, BadgeService badges,
                                   IntegrationEventPublisher events, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.badges = badges;
        this.events = events;
        this.clock = clock;
    }

    /** @return true 表示本次确实记账；false 表示重复事件已被忽略或增量为 0。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean record(long userId, String sourceEventId, ReputationReason reason, int delta, String refType,
                          long refId) {
        return record(userId, sourceEventId, reason, delta, refType, refId, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean record(long userId, String sourceEventId, ReputationReason reason, int delta, String refType,
                          long refId, String reversesEventId) {
        if (delta == 0 || ledger.exists(sourceEventId, userId, reason)) {
            return false;
        }
        ReputationAccount account = accounts.findByUserId(userId).orElseGet(() -> ReputationAccount.open(userId, 0));
        if (reason.isPoints()) {
            account.applyPoints(delta);
            accounts.save(account);
            ledger.append(LedgerEntry.of(userId, sourceEventId, reason, delta, refType, refId, clock.instant(),
                    reversesEventId));
            return true;
        }
        account.applyReputation(delta);
        accounts.save(account);
        ledger.append(LedgerEntry.of(userId, sourceEventId, reason, delta, refType, refId, clock.instant(),
                reversesEventId));
        events.publish(new ReputationChangedEvent(EventIds.next(), clock.instant(), userId, delta,
                account.reputation(), reason.name()));
        BadgePolicy.forReputation(account.reputation()).forEach(b -> badges.grant(userId, b));
        return true;
    }

    /**
     * 撤销冲正：反查原流水，只追加一条相反流水并记录 reversesEventId，绝不修改或删除原流水。
     * 徽章属于历史成就，不随撤销撤回（D4）。
     *
     * @return true 表示确实产生了冲正流水
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reverse(long userId, String reversalEventId, ReputationReason reason, String refType, long refId) {
        LedgerEntry original = ledger.findReversible(userId, reason, refType, refId).orElse(null);
        if (original == null || ledger.existsReversalOf(original.eventId(), userId, reason)) {
            return false;
        }
        return record(userId, reversalEventId, reason, ReputationRulebook.reversalDelta(original.delta()),
                refType, refId, original.eventId());
    }
}
