package com.campusoverflow.qa.application.command;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsementRevokedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.AnswerUnacceptedEvent;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.application.port.QuestionListCache;
import com.campusoverflow.qa.domain.AcceptanceReview;
import com.campusoverflow.qa.domain.AcceptanceReviewRepository;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.AnswerRepository;
import com.campusoverflow.qa.domain.EndorsementLog;
import com.campusoverflow.qa.domain.EndorsementLogRepository;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.QuestionRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：提交 / 编辑 / 删除回答，采纳与撤销采纳，教师认证与撤销认证。 */
@Service
public class AnswerCommandService {

    private static final String REASON_EDITED = "EDITED";

    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final AcceptanceReviewRepository reviews;
    private final EndorsementLogRepository endorsementLogs;
    private final IdentityApi identity;
    private final IntegrationEventPublisher events;
    private final QuestionListCache listCache;
    private final AuditTrail audit;
    private final Clock clock;

    public AnswerCommandService(QuestionRepository questions, AnswerRepository answers,
                                AcceptanceReviewRepository reviews, EndorsementLogRepository endorsementLogs,
                                IdentityApi identity, IntegrationEventPublisher events, QuestionListCache listCache,
                                AuditTrail audit, Clock clock) {
        this.questions = questions;
        this.answers = answers;
        this.reviews = reviews;
        this.endorsementLogs = endorsementLogs;
        this.identity = identity;
        this.events = events;
        this.listCache = listCache;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public long submit(Actor actor, long questionId, String body) {
        Question question = loadQuestion(questionId);
        ensureCourseMember(actor, question.courseId());
        Instant now = clock.instant();

        Answer answer = answers.save(Answer.submit(question, actor.userId(), MarkdownBody.ofAnswer(body), now));
        questions.adjustAnswerCount(questionId, 1);
        events.publish(new AnswerSubmittedEvent(EventIds.next(), now, answer.id(), questionId,
                actor.userId(), question.authorId(), question.title().value()));
        listCache.invalidateAll();
        audit.record("ANSWER_SUBMITTED", actor.userId(), "ANSWER", String.valueOf(answer.id()),
                question.title().value());
        return answer.id();
    }

    @Transactional
    public void edit(Actor actor, long answerId, String body) {
        Answer answer = loadAnswer(answerId);
        Question question = loadQuestion(answer.questionId());
        boolean manages = identity.canManageCourse(actor.userId(), question.courseId());
        Instant now = clock.instant();

        boolean wasEndorsed = answer.endorsedBy() != null;
        long version = answer.version();
        answer.edit(actor, manages, MarkdownBody.ofAnswer(body), now);
        answers.save(answer);
        if (wasEndorsed) {
            recordEndorsementRevocation(answer,
                    EndorsementLog.revoke(answerId, actor.userId(), version, REASON_EDITED, now));
        }
        audit.record("ANSWER_EDITED", actor.userId(), "ANSWER", String.valueOf(answerId), null);
    }

    @Transactional
    public void delete(Actor actor, long answerId) {
        Answer answer = loadAnswer(answerId);
        Question question = loadQuestion(answer.questionId());
        boolean manages = identity.canManageCourse(actor.userId(), question.courseId());
        boolean accepted = question.isAccepted(answerId);
        Instant now = clock.instant();

        answer.softDelete(actor, manages, accepted, now);
        answers.save(answer);
        questions.adjustAnswerCount(answer.questionId(), -1);
        if (accepted) {
            question.clearAcceptedAnswer();
            questions.save(question);
        }
        events.publish(new ContentDeletedEvent(EventIds.next(), now, "ANSWER", answerId, answer.questionId()));
        listCache.invalidateAll();
        audit.record("ANSWER_DELETED", actor.userId(), "ANSWER", String.valueOf(answerId), null);
    }

    /** 场景 R-2：采纳最佳答案。状态变更与 Outbox 事件在同一事务提交；声誉结算异步进行。 */
    @Transactional
    public void accept(Actor actor, long questionId, long answerId) {
        Question question = loadQuestion(questionId);
        Answer answer = loadAnswer(answerId);
        boolean manages = identity.canManageCourse(actor.userId(), question.courseId());

        question.accept(answer, actor, manages);
        questions.save(question);
        events.publish(new AnswerAcceptedEvent(EventIds.next(), clock.instant(), answerId, questionId,
                answer.authorId(), question.authorId(), actor.userId(), question.title().value()));
        listCache.invalidateAll();
        audit.record("ANSWER_ACCEPTED", actor.userId(), "ANSWER", String.valueOf(answerId),
                "question=" + questionId);
    }

    /** 提问者撤销采纳：奖励由 AnswerUnacceptedEvent 走相反流水冲正，不删账。 */
    @Transactional
    public void unaccept(Actor actor, long questionId) {
        Question question = loadQuestion(questionId);
        boolean manages = identity.canManageCourse(actor.userId(), question.courseId());
        revokeAcceptedAnswer(question, actor, manages);
    }

    /** 教师复核错误采纳：理由 + 预期版本（乐观锁），通过则留痕并撤销采纳，换绑答案需重新 accept。 */
    @Transactional
    public long reviewAcceptance(Actor actor, long questionId, ReviewAcceptanceCommand cmd) {
        Question question = loadQuestion(questionId);
        boolean manages = identity.canManageCourse(actor.userId(), question.courseId());
        if (!manages) {
            throw new ForbiddenException("QA-1029", "只有课程管理者可以复核采纳");
        }
        if (question.version() != cmd.expectedVersion()) {
            throw new ConflictException("QA-1030", "问题状态已变化，请刷新后重试");
        }
        if (question.acceptedAnswerId() == null) {
            throw new ConflictException("QA-1026", "该问题没有已采纳的回答");
        }
        Instant now = clock.instant();
        AcceptanceReview review = AcceptanceReview.approve(questionId, actor.userId(), question.version(),
                cmd.reason(), now);
        reviews.save(review);
        revokeAcceptedAnswer(question, actor, manages);
        audit.record("ACCEPTANCE_REVIEWED", actor.userId(), "QUESTION", String.valueOf(questionId), cmd.reason());
        return review.id();
    }

    @Transactional
    public void endorse(Actor actor, long answerId) {
        Answer answer = loadAnswer(answerId);
        Question question = loadQuestion(answer.questionId());
        boolean manages = identity.canManageCourse(actor.userId(), question.courseId());
        Instant now = clock.instant();

        answer.endorse(actor, manages);
        answers.save(answer);
        endorsementLogs.save(EndorsementLog.grant(answerId, actor.userId(), answer.version(), now));
        events.publish(new AnswerEndorsedEvent(EventIds.next(), now, answerId, question.id(),
                answer.authorId(), actor.userId(), question.title().value()));
        audit.record("ANSWER_ENDORSED", actor.userId(), "ANSWER", String.valueOf(answerId), null);
    }

    /** 撤销教师认证：留痕 + 冲正事件；endorsed_by 置空表示当前无有效认证。 */
    @Transactional
    public void revokeEndorsement(Actor actor, long answerId, String reason) {
        Answer answer = loadAnswer(answerId);
        Question question = loadQuestion(answer.questionId());
        boolean manages = identity.canManageCourse(actor.userId(), question.courseId());
        Instant now = clock.instant();
        long version = answer.version();

        EndorsementLog log = EndorsementLog.revoke(answerId, actor.userId(), version, reason, now);
        answer.revokeEndorsement(actor, manages);
        answers.save(answer);
        recordEndorsementRevocation(answer, log);
        audit.record("ANSWER_ENDORSEMENT_REVOKED", actor.userId(), "ANSWER", String.valueOf(answerId), reason);
    }

    private void recordEndorsementRevocation(Answer answer, EndorsementLog log) {
        endorsementLogs.save(log);
        events.publish(new AnswerEndorsementRevokedEvent(EventIds.next(), log.createdAt(), answer.id(),
                answer.questionId(), answer.authorId(), log.endorserId(), log.reason(), log.answerVersion()));
    }

    private void revokeAcceptedAnswer(Question question, Actor actor, boolean manages) {
        Long acceptedId = question.acceptedAnswerId();
        question.revokeAcceptance(actor, manages); // 无采纳时由领域抛出统一冲突
        questions.save(question);
        Answer answer = loadAnswer(acceptedId);
        events.publish(new AnswerUnacceptedEvent(EventIds.next(), clock.instant(), acceptedId, question.id(),
                answer.authorId(), question.authorId(), actor.userId(), question.title().value()));
        listCache.invalidateAll();
        audit.record("ANSWER_UNACCEPTED", actor.userId(), "ANSWER", String.valueOf(acceptedId),
                "question=" + question.id());
    }

    /** 回答只能落在课程成员可见的问题下（无课程问题不限制）。 */
    private void ensureCourseMember(Actor actor, Long courseId) {
        if (courseId != null && !identity.isCourseMember(actor.userId(), courseId)) {
            throw new ForbiddenException("QA-2005", "只有课程成员可以回答该课程下的问题");
        }
    }

    private Question loadQuestion(long id) {
        return questions.findById(id)
                .orElseThrow(() -> new NotFoundException("QA-2004", "问题不存在"));
    }

    private Answer loadAnswer(long id) {
        return answers.findById(id)
                .orElseThrow(() -> new NotFoundException("QA-2006", "回答不存在"));
    }
}
