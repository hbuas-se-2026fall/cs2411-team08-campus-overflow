package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.reputation.domain.BadgeRepository;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReputationQueryService {

    private final ReputationAccountRepository accounts;
    private final LedgerRepository ledger;
    private final BadgeRepository badges;
    private final IdentityApi identity;

    public ReputationQueryService(ReputationAccountRepository accounts, LedgerRepository ledger,
                                  BadgeRepository badges, IdentityApi identity) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.badges = badges;
        this.identity = identity;
    }

    public ReputationProfileView profile(long userId, Actor viewerOrNull) {
        UserSummary user = identity.findUser(userId)
                .orElseThrow(() -> new NotFoundException("REP-3001", "用户不存在"));
        ReputationAccount account = accounts.findByUserId(userId).orElse(null);
        // 展示下限 0：负声誉对外显示 0，账本与对账仍保留真实负值
        int reputation = account == null ? 0 : account.displayReputation();
        boolean self = viewerOrNull != null && viewerOrNull.is(userId);
        Integer available = self && account != null ? account.availablePoints() : null;
        Integer frozen = self && account != null ? account.frozenPoints() : null;
        List<ReputationProfileView.BadgeView> badgeViews = badges.findByUser(userId).stream()
                .map(b -> new ReputationProfileView.BadgeView(b.type().name(), b.type().displayName(),
                        b.type().description(), b.awardedAt()))
                .toList();
        return new ReputationProfileView(userId, user.displayName(), reputation, available, frozen, badgeViews);
    }

    /** 声誉流水属于个人数据：仅本人与管理员可查看。 */
    public PageResult<LedgerEntryView> ledger(long userId, Actor viewer, PageRequest page) {
        if (!viewer.is(userId) && !viewer.isAdmin()) {
            throw new ForbiddenException("REP-3002", "只能查看本人的声誉流水");
        }
        return this.ledger.findReputationByUser(userId, page).map(e -> new LedgerEntryView(e.id(), e.reason().name(),
                e.reason().label(), e.delta(), e.refType(), e.refId(), e.createdAt(), e.reversesEventId()));
    }
}
