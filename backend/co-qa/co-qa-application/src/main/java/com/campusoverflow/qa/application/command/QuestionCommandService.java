package com.campusoverflow.qa.application.command;

import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.api.event.QuestionEditedEvent;
import com.campusoverflow.qa.api.event.QuestionPostedEvent;
import com.campusoverflow.qa.application.port.ContentRenderer;
import com.campusoverflow.qa.application.port.QuestionListCache;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.QuestionRepository;
import com.campusoverflow.qa.domain.TagRepository;
import com.campusoverflow.qa.domain.Tags;
import com.campusoverflow.qa.domain.Title;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：发布 / 编辑 / 删除问题。 */
@Service
public class QuestionCommandService {

    static final int PLAIN_TEXT_FOR_INDEX = 2000;

    private final QuestionRepository questions;
    private final TagRepository tags;
    private final IdentityApi identity;
    private final IntegrationEventPublisher events;
    private final ContentRenderer renderer;
    private final QuestionListCache listCache;
    private final AuditTrail audit;
    private final Clock clock;

    public QuestionCommandService(QuestionRepository questions, TagRepository tags, IdentityApi identity,
                                  IntegrationEventPublisher events, ContentRenderer renderer,
                                  QuestionListCache listCache, AuditTrail audit, Clock clock) {
        this.questions = questions;
        this.tags = tags;
        this.identity = identity;
        this.events = events;
        this.renderer = renderer;
        this.listCache = listCache;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public long post(Actor actor, PostQuestionCommand cmd) {
        Long courseId = cmd.courseId();
        ensureCourseOpenAndJoined(actor, courseId);
        Instant now = clock.instant();
        Title title = new Title(cmd.title());
        MarkdownBody body = MarkdownBody.ofQuestion(cmd.body());
        Tags tagSet = Tags.of(cmd.tags());

        Question question = questions.save(Question.post(actor.userId(), courseId, title, body, tagSet, now));
        tags.adjustUsage(tagSet.names(), 1);
        events.publish(new QuestionPostedEvent(EventIds.next(), now, question.id(), actor.userId(), courseId,
                title.value(), renderer.toPlainText(cmd.body(), PLAIN_TEXT_FOR_INDEX), tagSet.names()));
        listCache.invalidateAll();
        audit.record("QUESTION_POSTED", actor.userId(), "QUESTION", String.valueOf(question.id()), title.value());
        return question.id();
    }

    @Transactional
    public void edit(Actor actor, long questionId, EditQuestionCommand cmd) {
        Question question = load(questionId);
        boolean manages = identity.canManageCourse(actor.userId(), question.courseId());
        Instant now = clock.instant();
        Title title = new Title(cmd.title());
        MarkdownBody body = MarkdownBody.ofQuestion(cmd.body());
        Tags tagSet = Tags.of(cmd.tags());

        List<String> oldTags = question.tags().names();
        question.edit(actor, manages, title, body, tagSet, now);
        questions.save(question);

        tags.adjustUsage(oldTags, -1);
        tags.adjustUsage(tagSet.names(), 1);
        events.publish(new QuestionEditedEvent(EventIds.next(), now, questionId, title.value(),
                renderer.toPlainText(cmd.body(), PLAIN_TEXT_FOR_INDEX), tagSet.names()));
        listCache.invalidateAll();
        audit.record("QUESTION_EDITED", actor.userId(), "QUESTION", String.valueOf(questionId), title.value());
    }

    @Transactional
    public void delete(Actor actor, long questionId) {
        Question question = load(questionId);
        boolean manages = identity.canManageCourse(actor.userId(), question.courseId());
        Instant now = clock.instant();

        question.softDelete(actor, manages, now);
        questions.save(question);
        tags.adjustUsage(question.tags().names(), -1);
        events.publish(new ContentDeletedEvent(EventIds.next(), now, "QUESTION", questionId, questionId));
        listCache.invalidateAll();
        audit.record("QUESTION_DELETED", actor.userId(), "QUESTION", String.valueOf(questionId),
                question.title().value());
    }

    /** 教师关闭问题：关闭后不可再回答、采纳；不发集成事件（下游无需感知治理状态）。 */
    @Transactional
    public void close(Actor actor, long questionId) {
        Question question = load(questionId);
        boolean manages = identity.canManageCourse(actor.userId(), question.courseId());

        question.close(actor, manages);
        questions.save(question);
        listCache.invalidateAll();
        audit.record("QUESTION_CLOSED", actor.userId(), "QUESTION", String.valueOf(questionId),
                question.title().value());
    }

    /** 归档课程只读（FF-5 课程状态约束）：课程必须存在、未归档，且提问者是课程成员。 */
    private void ensureCourseOpenAndJoined(Actor actor, Long courseId) {
        if (courseId == null) {
            return;
        }
        CourseSummary course = identity.findCourse(courseId)
                .orElseThrow(() -> new NotFoundException("QA-2001", "课程不存在"));
        if (!"OPEN".equals(course.status())) {
            throw new BusinessRuleException("QA-2002", "课程已归档，不能在归档课程中提问");
        }
        if (!identity.isCourseMember(actor.userId(), courseId)) {
            throw new ForbiddenException("QA-2003", "只有课程成员可以在该课程下提问");
        }
    }

    private Question load(long questionId) {
        return questions.findById(questionId)
                .orElseThrow(() -> new NotFoundException("QA-2004", "问题不存在"));
    }
}
