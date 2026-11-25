package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import java.util.Optional;

public interface CourseBudgetRepository {

    Optional<CourseBudget> findByCourseId(long courseId);

    CourseBudget save(CourseBudget budget);

    void append(BudgetAdjustment adjustment);

    PageResult<BudgetAdjustment> findAdjustments(long courseId, PageRequest page);

    boolean exists(String eventId, long courseId);
}
