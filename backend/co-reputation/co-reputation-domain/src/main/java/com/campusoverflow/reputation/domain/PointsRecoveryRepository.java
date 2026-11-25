package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import java.util.Optional;

public interface PointsRecoveryRepository {

    Optional<PointsRecovery> findPendingByUser(long userId);

    Optional<PointsRecovery> findById(long id);

    PointsRecovery save(PointsRecovery recovery);

    PageResult<PointsRecovery> findAll(PageRequest page);
}
