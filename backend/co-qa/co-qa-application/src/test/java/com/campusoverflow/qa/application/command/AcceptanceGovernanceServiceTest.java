package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsementRevokedEvent;
import com.campusoverflow.qa.api.event.AnswerUnacceptedEvent;
import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingCache;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryAcceptanceReviews;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryAnswers;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryEndorsementLogs;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.application.fake.QaTestFixtures.PassthroughRenderer;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingAudit;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingEvents;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingTags;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import com.campusoverflow.qa.domain.EndorsementLog;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.QuestionStatus;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 治理流程：撤销采纳冲正、教师复核乐观锁、认证历史留痕、关闭问题。 */
class AcceptanceGovernanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final InMemoryAnswers answers = new InMemoryAnswers();
    private final InMemoryAcceptanceReviews reviews = new InMemoryAcceptanceReviews();
    private final InMemoryEndorsementLogs endorsementLogs = new InMemoryEndorsementLogs();
    private final RecordingEvents events = new RecordingEvents();
    private final CountingCache cache = new CountingCache();
    private final RecordingAudit audit = new RecordingAudit();
    private final StubIdentity identity = new StubIdentity()
            .withUser(1, "小明", Role.STUDENT).withUser(2, "小红", Role.STUDENT)
            .withUser(9, "王老师", Role.TEACHER);

    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);
    private final Actor teacher = new Actor(9, "王老师", Role.TEACHER);

    private AnswerCommandService answerService;
    private QuestionCommandService questionService;
    private long questionId;
    private long answerId;

    @BeforeEach
    void setUp() {
        answerService = new AnswerCommandService(questions, answers, reviews, endorsementLogs, identity,
                events, cache, audit, clock);
        questionService = new QuestionCommandService(questions, new RecordingTags(), identity, events,
                new PassthroughRenderer(), cache, audit, clock);
        questionId = questionService.post(ming, new PostQuestionCommand(null, "红黑树插入后为什么必须旋转？",
                "变色为什么不够？希望有直观解释，最好能给出一个反例。", List.of("数据结构")));
        answerId = answerService.submit(hong, questionId, "关键在于黑高这个不变量，仅靠变色无法调整。");
        answerService.accept(ming, questionId, answerId);
        identity.withCourseManager(9L);
        events.published.clear();
    }

    private Question question() {
        return questions.findById(questionId).orElseThrow();
    }

    @Test
    @DisplayName("撤销采纳：发冲正事件、留审计、失效缓存，状态回到 OPEN；重复撤销冲突")
    void unacceptPublishesReversalEvent() {
        int invalidationsBefore = cache.invalidations;
        answerService.unaccept(ming, questionId);

        List<AnswerUnacceptedEvent> unaccepted = events.ofType(AnswerUnacceptedEvent.class);
        assertThat(unaccepted).hasSize(1);
        assertThat(unaccepted.get(0).answerId()).isEqualTo(answerId);
        assertThat(unaccepted.get(0).answerAuthorId()).isEqualTo(hong.userId());
        assertThat(unaccepted.get(0).revokedById()).isEqualTo(ming.userId());
        assertThat(audit.actions).contains("ANSWER_UNACCEPTED");
        assertThat(cache.invalidations).isGreaterThan(invalidationsBefore);
        assertThat(question().acceptedAnswerId()).isNull();
        assertThat(question().status()).isEqualTo(QuestionStatus.OPEN);

        assertThatThrownBy(() -> answerService.unaccept(ming, questionId)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("教师复核：非管理者拒绝；预期版本不符冲突；通过后留痕并撤销采纳，可再次采纳换绑")
    void reviewRequiresManagerAndFreshVersion() {
        assertThatThrownBy(() -> answerService.reviewAcceptance(hong, questionId,
                new ReviewAcceptanceCommand(0, "采纳错了"))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> answerService.reviewAcceptance(teacher, questionId,
                new ReviewAcceptanceCommand(1, "采纳错了"))).isInstanceOf(ConflictException.class);

        long reviewId = answerService.reviewAcceptance(teacher, questionId,
                new ReviewAcceptanceCommand(0, "该答案结论有误，需要重新采纳"));
        assertThat(reviewId).isPositive();
        assertThat(reviews.findByQuestion(questionId)).hasSize(1);
        assertThat(reviews.saved.get(0).result()).isEqualTo("APPROVED");
        assertThat(reviews.saved.get(0).reason()).contains("重新采纳");
        assertThat(reviews.saved.get(0).questionVersion()).isZero();
        assertThat(audit.actions).contains("ACCEPTANCE_REVIEWED");
        assertThat(events.ofType(AnswerUnacceptedEvent.class)).hasSize(1);
        assertThat(question().acceptedAnswerId()).isNull();

        long another = answerService.submit(hong, questionId, "补充了完整推导与反例的更正回答。");
        answerService.accept(ming, questionId, another);
        assertThat(question().acceptedAnswerId()).isEqualTo(another);
        assertThat(question().status()).isEqualTo(QuestionStatus.RESOLVED);
    }

    @Test
    @DisplayName("认证历史：授予 GRANT、撤销 REVOKE，事件携带原因与答案版本；重复撤销冲突")
    void endorsementHistoryAndReversal() {
        answerService.endorse(teacher, answerId);
        assertThat(endorsementLogs.findByAnswer(answerId)).extracting(EndorsementLog::action)
                .containsExactly(EndorsementLog.Action.GRANT);
        assertThat(events.ofType(AnswerEndorsedEvent.class)).hasSize(1);

        events.published.clear();
        answerService.revokeEndorsement(teacher, answerId, "答案已过时");

        assertThat(endorsementLogs.findByAnswer(answerId)).extracting(EndorsementLog::action)
                .containsExactly(EndorsementLog.Action.GRANT, EndorsementLog.Action.REVOKE);
        assertThat(endorsementLogs.saved.get(1).reason()).isEqualTo("答案已过时");
        List<AnswerEndorsementRevokedEvent> revoked = events.ofType(AnswerEndorsementRevokedEvent.class);
        assertThat(revoked).hasSize(1);
        assertThat(revoked.get(0).reason()).isEqualTo("答案已过时");
        assertThat(revoked.get(0).answerVersion()).isZero();
        assertThat(answers.findById(answerId).orElseThrow().endorsedBy()).isNull();
        assertThat(audit.actions).contains("ANSWER_ENDORSEMENT_REVOKED");

        assertThatThrownBy(() -> answerService.revokeEndorsement(teacher, answerId, "再撤一次"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("已认证答案被编辑：认证自动失效并写 REVOKE(EDITED) 历史与冲正事件")
    void editingEndorsedAnswerRevokesEndorsement() {
        answerService.endorse(teacher, answerId);
        events.published.clear();

        answerService.edit(hong, answerId, "这是被回答者本人更新后的正文内容");

        assertThat(endorsementLogs.findByAnswer(answerId)).extracting(EndorsementLog::action)
                .containsExactly(EndorsementLog.Action.GRANT, EndorsementLog.Action.REVOKE);
        assertThat(endorsementLogs.saved.get(1).reason()).isEqualTo("EDITED");
        List<AnswerEndorsementRevokedEvent> revoked = events.ofType(AnswerEndorsementRevokedEvent.class);
        assertThat(revoked).hasSize(1);
        assertThat(revoked.get(0).reason()).isEqualTo("EDITED");
        assertThat(answers.findById(answerId).orElseThrow().endorsedBy()).isNull();
    }

    @Test
    @DisplayName("关闭问题：仅管理者可关；关闭后不能再提交回答")
    void closeRequiresManagerAndBlocksAnswers() {
        assertThatThrownBy(() -> questionService.close(hong, questionId)).isInstanceOf(ForbiddenException.class);

        questionService.close(teacher, questionId);
        assertThat(question().status()).isEqualTo(QuestionStatus.CLOSED);
        assertThat(audit.actions).contains("QUESTION_CLOSED");

        assertThatThrownBy(() -> answerService.submit(hong, questionId, "一个试图在关闭后追加的回答内容"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("关闭");
    }
}
