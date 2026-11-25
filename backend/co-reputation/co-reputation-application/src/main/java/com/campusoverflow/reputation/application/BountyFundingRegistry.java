package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.domain.BountySource;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.domain.BusinessRuleException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 按来源查找资金策略。BountyService 只依赖本注册表，不感知具体来源（DIP）。 */
@Component
public class BountyFundingRegistry {

    private final Map<BountySource, BountyFunding> strategies = new EnumMap<>(BountySource.class);

    public BountyFundingRegistry(List<BountyFunding> fundings) {
        fundings.forEach(f -> strategies.put(f.source(), f));
    }

    public BountyFunding fundingFor(BountySource source) {
        BountyFunding funding = strategies.get(source);
        if (funding == null) {
            throw new BusinessRuleException("REP-2009", "不支持的悬赏资金来源：" + source);
        }
        return funding;
    }

    /** 兼容既有单元测试的构造：只启用个人账户来源。 */
    public static BountyFundingRegistry personalOnly(ReputationAccountRepository accounts) {
        return new BountyFundingRegistry(List.of(new PersonalPointsFunding(accounts)));
    }
}
