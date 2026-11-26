package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.domain.BountySource;

/**
 * 悬赏资金来源策略。新增来源只需新增一个 @Component 实现并声明 source()，
 * BountyService 与 Registry 都不用改（OCP，避免按类型 if/switch 分支）。
 */
public interface BountyFunding {

    BountySource source();

    void freeze(long sourceId, int points, BountyOperationContext context);

    void spend(long sourceId, int points, BountyOperationContext context);

    void refund(long sourceId, int points, BountyOperationContext context);
}
