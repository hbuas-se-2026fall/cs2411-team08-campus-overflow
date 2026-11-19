package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryAnswers;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryComments;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingEvents;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.Comment;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.TargetType;
import com.campusoverflow.qa.domain.Tags;
import com.campusoverflow.qa.domain.Title;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 评论命令服务用例测试：目标校验、回复层级、事件发布。 */
class CommentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final InMemoryAnswers answers = new InMemoryAnswers();
    private final InMemoryComments comments = new InMemoryComments();
    private final RecordingEvents events = new RecordingEvents();

    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);

    private CommentService service;
    private long questionId;
    private long answerId;

    @BeforeEach
    void setUp() {
        service = new CommentService(comments, questions, answers, events, clock);
        Question q = questions.save(Question.post(ming.userId(), 100L, new Title("红黑树插入后为什么必须旋转？"),
                MarkdownBody.ofQuestion("变色为什么不够？希望有直观解释，最好能给出一个反例。"),
                Tags.of(List.of("数据结构", "红黑树")), NOW));
        questionId = q.id();
        Answer a = answers.save(Answer.submit(q, hong.userId(),
                MarkdownBody.ofAnswer("关键在于黑高这个不变量，仅靠变色无法调整。"), NOW));
        answerId = a.id();
    }

    @Test
    @DisplayName("评论问题：questionId 即目标本身，发布 CommentPostedEvent")
    void commentOnQuestionPublishesEvent() {
        long id = service.post(hong, new PostCommentCommand(TargetType.QUESTION, questionId, null,
                "可以参考 CLRS 第 13 章的旋转图示。"));

        Comment saved = comments.findById(id).orElseThrow();
        assertThat(saved.questionId()).isEqualTo(questionId);
        assertThat(saved.parentId()).isNull();

        List<CommentPostedEvent> posted = events.ofType(CommentPostedEvent.class);
        assertThat(posted).hasSize(1);
        assertThat(posted.get(0).commentId()).isEqualTo(id);
        assertThat(posted.get(0).questionId()).isEqualTo(questionId);
        assertThat(posted.get(0).authorId()).isEqualTo(hong.userId());
    }

    @Test
    @DisplayName("评论回答：questionId 取自回答本身，而非客户端传入")
    void commentOnAnswerDerivesQuestionId() {
        long id = service.post(ming, new PostCommentCommand(TargetType.ANSWER, answerId, null,
                "请问第二步为什么要先变色再旋转？"));

        assertThat(comments.findById(id).orElseThrow().questionId()).isEqualTo(questionId);
    }

    @Test
    @DisplayName("回复一层成功；回复的回复被拒绝；跨目标回复被拒绝")
    void replyDepthAndTargetRules() {
        long topId = service.post(ming, new PostCommentCommand(TargetType.ANSWER, answerId, null,
                "请问第二步为什么要先变色再旋转？"));
        long replyId = service.post(hong, new PostCommentCommand(TargetType.ANSWER, answerId, topId,
                "因为要先维持红黑性质，再通过旋转恢复黑高。"));
        assertThat(comments.findById(replyId).orElseThrow().parentId()).isEqualTo(topId);

        Comment reply = comments.findById(replyId).orElseThrow();
        assertThatThrownBy(() -> service.post(ming, new PostCommentCommand(TargetType.ANSWER, answerId, replyId,
                "再回复一层的评论内容。"))).isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> service.post(ming, new PostCommentCommand(TargetType.QUESTION, questionId, topId,
                "挂错目标的回复内容。"))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("目标不存在或已删除不能评论")
    void invalidTargetsAreRejected() {
        assertThatThrownBy(() -> service.post(ming, new PostCommentCommand(TargetType.QUESTION, 999L, null,
                "评论一个不存在的问题。"))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.post(ming, new PostCommentCommand(TargetType.ANSWER, 999L, null,
                "评论一个不存在的回答。"))).isInstanceOf(NotFoundException.class);

        Question deleted = questions.findById(questionId).orElseThrow();
        deleted.softDelete(ming, false, NOW.plusSeconds(60));
        assertThatThrownBy(() -> service.post(ming, new PostCommentCommand(TargetType.QUESTION, questionId, null,
                "评论一个已删除的问题。"))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("回复的评论不存在时返回 404")
    void missingParentCommentIsNotFound() {
        assertThatThrownBy(() -> service.post(ming, new PostCommentCommand(TargetType.ANSWER, answerId, 42L,
                "回复一个不存在的评论。"))).isInstanceOf(NotFoundException.class);
    }
}
