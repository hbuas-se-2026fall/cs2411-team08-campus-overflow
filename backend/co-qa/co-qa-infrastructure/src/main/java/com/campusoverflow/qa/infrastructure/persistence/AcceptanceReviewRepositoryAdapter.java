package com.campusoverflow.qa.infrastructure.persistence;

import com.campusoverflow.qa.domain.AcceptanceReview;
import com.campusoverflow.qa.domain.AcceptanceReviewRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class AcceptanceReviewRepositoryAdapter implements AcceptanceReviewRepository {

    private final AcceptanceReviewJpaRepository jpa;

    public AcceptanceReviewRepositoryAdapter(AcceptanceReviewJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(AcceptanceReview review) {
        AcceptanceReviewJpaEntity saved = jpa.save(new AcceptanceReviewJpaEntity(review.questionId(),
                review.reviewerId(), review.questionVersion(), review.result(), review.reason(), review.createdAt()));
        review.assignId(saved.getId());
    }

    @Override
    public List<AcceptanceReview> findByQuestion(long questionId) {
        return jpa.findByQuestionIdOrderByIdAsc(questionId).stream()
                .map(e -> AcceptanceReview.reconstitute(e.getId(), e.getQuestionId(), e.getReviewerId(),
                        e.getQuestionVersion(), e.getResult(), e.getReason(), e.getCreatedAt()))
                .toList();
    }
}
