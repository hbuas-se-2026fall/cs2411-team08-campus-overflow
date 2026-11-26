package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「我的积分」查询用例：me 语义由接口层传入的 Actor 决定，天然只服务本人。
 * 与 ReputationQueryService 分开，避免把发布语言、声誉档案、积分账户塞进一个胖服务（SRP/ISP）。
 */
@Service
@Transactional(readOnly = true)
public class PointsQueryService {

    private final ReputationAccountRepository accounts;
    private final LedgerRepository ledger;

    public PointsQueryService(ReputationAccountRepository accounts, LedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    public PointsView balance(Actor actor) {
        ReputationAccount account = accounts.findByUserId(actor.userId()).orElse(null);
        int available = account == null ? 0 : account.availablePoints();
        int frozen = account == null ? 0 : account.frozenPoints();
        int pending = account == null ? 0 : account.pendingRecovery();
        return new PointsView(actor.userId(), available, frozen, available + frozen, pending);
    }

    /** 只返回 POINTS 币种流水，声誉流水不在积分账单里出现。 */
    public PageResult<LedgerEntryView> history(Actor actor, PageRequest page) {
        return ledger.findPointsByUser(actor.userId(), page).map(PointsQueryService::toView);
    }

    private static LedgerEntryView toView(LedgerEntry e) {
        return new LedgerEntryView(e.id(), e.reason().name(), e.reason().label(), e.delta(), e.refType(),
                e.refId(), e.createdAt(), e.reversesEventId());
    }
}
