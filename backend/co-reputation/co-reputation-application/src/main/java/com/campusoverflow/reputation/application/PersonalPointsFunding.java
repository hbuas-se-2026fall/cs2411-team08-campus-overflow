package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.domain.BountySource;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.domain.BusinessRuleException;
import org.springframework.stereotype.Component;

/**
 * 个人账户资金来源：冻结可用积分，退款时把冻结退回可用。
 * 冻结→可用属于同一账户内部划转，不写 rep_ledger（避免污染 POINTS 币种事由），
 * 终态唯一性由 BountyRepository.tryExpire 条件更新 + BountyClosedEvent + 审计共同保证。
 */
@Component
public class PersonalPointsFunding implements BountyFunding {

    private final ReputationAccountRepository accounts;

    public PersonalPointsFunding(ReputationAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public BountySource source() {
        return BountySource.PERSONAL;
    }

    @Override
    public void freeze(long sourceId, int points, BountyOperationContext context) {
        ReputationAccount account = accounts.findByUserId(sourceId)
                .orElseThrow(() -> new BusinessRuleException("REP-4002", "声誉账户不存在，无法发起悬赏"));
        account.freezePoints(points);
        accounts.save(account);
    }

    @Override
    public void spend(long sourceId, int points, BountyOperationContext context) {
        ReputationAccount account = accounts.findByUserId(sourceId).orElse(null);
        if (account == null) {
            return;
        }
        account.spendFrozen(points);
        accounts.save(account);
    }

    @Override
    public void refund(long sourceId, int points, BountyOperationContext context) {
        ReputationAccount account = accounts.findByUserId(sourceId).orElse(null);
        if (account == null) {
            return;
        }
        account.refundFrozen(points);
        accounts.save(account);
    }
}
