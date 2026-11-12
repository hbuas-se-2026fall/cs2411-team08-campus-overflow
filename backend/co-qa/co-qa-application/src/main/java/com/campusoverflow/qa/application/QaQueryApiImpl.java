package com.campusoverflow.qa.application;

import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.QuestionRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class QaQueryApiImpl implements QaQueryApi {

    private final QuestionRepository questions;

    public QaQueryApiImpl(QuestionRepository questions) {
        this.questions = questions;
    }

    @Override
    public Optional<QuestionRef> findQuestion(long questionId) {
        // 走领域仓储而非读模型：QuestionRef 带 deleted 标记，需要能感知已删除问题
        return questions.findById(questionId)
                .map(this::toRef);
    }

    private QuestionRef toRef(Question q) {
        return new QuestionRef(q.id(), q.authorId(), q.courseId(), q.title().value(), q.acceptedAnswerId(),
                q.isDeleted());
    }
}
