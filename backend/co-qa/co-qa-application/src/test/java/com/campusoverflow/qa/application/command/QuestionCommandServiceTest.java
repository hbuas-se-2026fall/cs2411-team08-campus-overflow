package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.api.event.QuestionEditedEvent;
import com.campusoverflow.qa.api.event.QuestionPostedEvent;
import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingCache;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.application.fake.QaTestFixtures.PassthroughRenderer;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingAudit;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingEvents;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingTags;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ForbiddenException;
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

/** 问题命令服务用例测试：课程校验、事件发布、标签计数、缓存失效与审计。 */
class QuestionCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final RecordingTags tags = new RecordingTags();
    private final RecordingEvents events = new RecordingEvents();
    private final CountingCache cache = new CountingCache();
    private final RecordingAudit audit = new RecordingAudit();
    private final StubIdentity identity = new StubIdentity()
            .withUser(1, "小明", Role.STUDENT).withUser(9, "王老师", Role.TEACHER);

    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);

    private QuestionCommandService service;

    @BeforeEach
    void setUp() {
        service = new QuestionCommandService(questions, tags, identity, events, new PassthroughRenderer(),
                cache, audit, clock);
    }

    @Test
    @DisplayName("发布问题：发布事件（含搜索用纯文本）、标签计数 +1、列表缓存失效、审计")
    void postPublishesEventAndAdjustsTagUsage() {
        long id = service.post(ming, new PostQuestionCommand(100L, "红黑树插入后为什么必须旋转？",
                "希望有直观解释，最好能给出一个反例说明只变色不够。", List.of("#数据结构", "红黑树")));

        assertThat(id).isPositive();
        List<QuestionPostedEvent> posted = events.ofType(QuestionPostedEvent.class);
        assertThat(posted).hasSize(1);
        assertThat(posted.get(0).questionId()).isEqualTo(id);
        assertThat(posted.get(0).authorId()).isEqualTo(ming.userId());
        assertThat(posted.get(0).tags()).containsExactly("数据结构", "红黑树");
        assertThat(posted.get(0).plainText()).doesNotContain("#");
        assertThat(tags.netDeltaOf("红黑树")).isEqualTo(1);
        assertThat(cache.invalidations).isEqualTo(1);
        assertThat(audit.actions).contains("QUESTION_POSTED");
    }

    @Test
    @DisplayName("归档课程不能提问，非课程成员不能提问")
    void archivedCourseAndNonMemberAreRejected() {
        identity.withCourse(100L, "ARCHIVED");
        assertThatThrownBy(() -> service.post(ming, new PostQuestionCommand(100L, "归档课程里发布的提问标题",
                "正文内容足够长以通过校验。", List.of("测试")))).isInstanceOf(BusinessRuleException.class);

        identity.withCourse(100L, "OPEN").excludeFromCourse(ming.userId());
        assertThatThrownBy(() -> service.post(ming, new PostQuestionCommand(100L, "非成员发布的提问标题",
                "正文内容足够长以通过校验。", List.of("测试")))).isInstanceOf(ForbiddenException.class);
        assertThat(questions.answerCountDeltas).isEmpty();
    }

    @Test
    @DisplayName("编辑：作者可以编辑并发布编辑事件，旧标签 -1 新标签 +1")
    void authorEditRepublishesEventAndAdjustsTags() {
        long id = service.post(ming, new PostQuestionCommand(null, "红黑树插入后为什么必须旋转？",
                "希望有直观解释，最好能给出一个反例说明只变色不够。", List.of("红黑树")));
        tags.calls.clear();

        service.edit(ming, id, new EditQuestionCommand("红黑树插入后如何判断旋转方向？",
                "变色为什么不够？更新后的问题描述，希望给出完整推导。", List.of("红黑树", "算法")));

        List<QuestionEditedEvent> edited = events.ofType(QuestionEditedEvent.class);
        assertThat(edited).hasSize(1);
        assertThat(edited.get(0).title()).contains("旋转方向");
        assertThat(tags.netDeltaOf("红黑树")).isZero();
        assertThat(tags.netDeltaOf("算法")).isEqualTo(1);
        assertThat(cache.invalidations).isEqualTo(2);
    }

    @Test
    @DisplayName("编辑/删除：路人无权操作；提问者不能删除有回答的问题；教师可以删除任意问题")
    void authorizationAndDeletionRules() {
        long id = service.post(ming, new PostQuestionCommand(null, "红黑树插入后为什么必须旋转？",
                "希望有直观解释，最好能给出一个反例说明只变色不够。", List.of("红黑树")));

        assertThatThrownBy(() -> service.edit(hong, id, new EditQuestionCommand("红黑树插入后如何判断旋转方向？",
                "路人篡改的正文内容，应该被权限校验直接拒绝。", List.of("红黑树"))))
                .isInstanceOf(ForbiddenException.class);

        assertThatThrownBy(() -> service.delete(hong, id)).isInstanceOf(ForbiddenException.class);

        service.delete(ming, id);
        List<ContentDeletedEvent> deleted = events.ofType(ContentDeletedEvent.class);
        assertThat(deleted).hasSize(1);
        assertThat(deleted.get(0).targetType()).isEqualTo("QUESTION");
        assertThat(tags.netDeltaOf("红黑树")).isZero();
        assertThat(cache.invalidations).isEqualTo(2);
    }

    @Test
    @DisplayName("提问者不能删除有回答的问题（教师可删），问题不存在返回 404")
    void askerCannotDeleteAnsweredQuestionButManagerCan() {
        Question answered = Question.reconstitute(10L, ming.userId(), 100L, new com.campusoverflow.qa.domain.Title(
                        "一个已经有回答的问题标题"),
                com.campusoverflow.qa.domain.MarkdownBody.ofQuestion("这个问题已经有了一个回答，提问者不能删除。"),
                com.campusoverflow.qa.domain.Tags.of(List.of("红黑树")), null, 0, 1, 0, NOW, NOW, null, 0);
        questions.save(answered);

        assertThatThrownBy(() -> service.delete(ming, 10L)).isInstanceOf(BusinessRuleException.class);

        identity.withCourseManager(9L);
        service.delete(new Actor(9, "王老师", Role.TEACHER), 10L);
        assertThat(audit.actions).contains("QUESTION_DELETED");

        assertThatThrownBy(() -> service.delete(ming, 999L)).isInstanceOf(NotFoundException.class);
    }
}
