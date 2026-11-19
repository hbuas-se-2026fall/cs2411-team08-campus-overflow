package com.campusoverflow.qa.domain;

import java.util.List;

/** 采纳复核记录的持久化端口。 */
public interface AcceptanceReviewRepository {

    void save(AcceptanceReview review);

    List<AcceptanceReview> findByQuestion(long questionId);
}
