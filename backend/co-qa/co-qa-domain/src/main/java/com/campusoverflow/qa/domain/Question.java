package com.campusoverflow.qa.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import java.time.Instant;
import java.util.Objects;

/**
 * 问题聚合根——问答核心不变量的守护者。
 * <ul>
 *   <li>最多只有一个被采纳的答案，且采纳后不可更改（撤销须走显式复核/撤销用例）；</li>
 *   <li>只有提问者或课程管理者（教师/助教/管理员）可以采纳；</li>
 *   <li>已删除或已关闭的问题不能再被回答、编辑或采纳；</li>
 *   <li>提问者只能删除尚无回答的问题，课程管理者可删除任意问题；</li>
 *   <li>status 与软删除双轨：查询以 deletedAt 为准，status 表达解答进度与治理状态。</li>
 * </ul>
 * score / answerCount / viewCount 为反规范化计数器，由仓储以原子 SQL 维护，不在此处修改。
 */
public class Question {

    private Long id;
    private final long authorId;
    private final Long courseId;
    private Title title;
    private MarkdownBody body;
    private Tags tags;
    private Long acceptedAnswerId;
    private QuestionStatus status;
    private final int score;
    private final int answerCount;
    private final int viewCount;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private final long version;

    private Question(Long id, long authorId, Long courseId, Title title, MarkdownBody body, Tags tags,
                     Long acceptedAnswerId, QuestionStatus status, int score, int answerCount, int viewCount,
                     Instant createdAt, Instant updatedAt, Instant deletedAt, long version) {
        this.id = id;
        this.authorId = authorId;
        this.courseId = courseId;
        this.title = Objects.requireNonNull(title);
        this.body = Objects.requireNonNull(body);
        this.tags = Objects.requireNonNull(tags);
        this.acceptedAnswerId = acceptedAnswerId;
        this.status = Objects.requireNonNull(status);
        this.score = score;
        this.answerCount = answerCount;
        this.viewCount = viewCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.version = version;
    }

    public static Question post(long authorId, Long courseId, Title title, MarkdownBody body, Tags tags, Instant now) {
        return new Question(null, authorId, courseId, title, body, tags, null, QuestionStatus.OPEN,
                0, 0, 0, now, null, null, 0);
    }

    /** 旧调用点兼容：状态由软删除与采纳引用推导，不必让调用方感知新增字段。 */
    public static Question reconstitute(Long id, long authorId, Long courseId, Title title, MarkdownBody body,
                                        Tags tags, Long acceptedAnswerId, int score, int answerCount, int viewCount,
                                        Instant createdAt, Instant updatedAt, Instant deletedAt, long version) {
        QuestionStatus derived = deletedAt != null ? QuestionStatus.DELETED
                : acceptedAnswerId != null ? QuestionStatus.RESOLVED : QuestionStatus.OPEN;
        return reconstitute(id, authorId, courseId, title, body, tags, acceptedAnswerId, derived, score,
                answerCount, viewCount, createdAt, updatedAt, deletedAt, version);
    }

    public static Question reconstitute(Long id, long authorId, Long courseId, Title title, MarkdownBody body,
                                        Tags tags, Long acceptedAnswerId, QuestionStatus status, int score,
                                        int answerCount, int viewCount, Instant createdAt, Instant updatedAt,
                                        Instant deletedAt, long version) {
        return new Question(id, authorId, courseId, title, body, tags, acceptedAnswerId, status, score,
                answerCount, viewCount, createdAt, updatedAt, deletedAt, version);
    }

    public void edit(Actor actor, boolean actorManagesCourse, Title newTitle, MarkdownBody newBody, Tags newTags,
                     Instant now) {
        ensureNotDeleted();
        ensureCanManage(actor, actorManagesCourse);
        this.title = Objects.requireNonNull(newTitle);
        this.body = Objects.requireNonNull(newBody);
        this.tags = Objects.requireNonNull(newTags);
        this.updatedAt = now;
    }

    /** 采纳最佳答案：只把 OPEN 建议为 RESOLVED，不覆盖 CLOSED 等治理状态。 */
    public void accept(Answer answer, Actor actor, boolean actorManagesCourse) {
        ensureNotDeleted();
        ensureOpen();
        ensureCanManage(actor, actorManagesCourse);
        Objects.requireNonNull(answer, "answer 不能为空");
        if (answer.questionId() != this.id) {
            throw new BusinessRuleException("QA-1007", "回答不属于该问题，不能采纳");
        }
        if (answer.isDeleted()) {
            throw new BusinessRuleException("QA-1008", "已删除的回答不能被采纳");
        }
        if (this.acceptedAnswerId != null) {
            throw new ConflictException("QA-1009", "该问题已有被采纳的回答，且采纳后不可更改");
        }
        this.acceptedAnswerId = answer.id();
        if (this.status == QuestionStatus.OPEN) {
            this.status = QuestionStatus.RESOLVED;
        }
    }

    /** 撤销采纳：提问者或课程管理者；RESOLVED 回到 OPEN，CLOSED 保持治理状态不被覆盖。 */
    public void revokeAcceptance(Actor actor, boolean actorManagesCourse) {
        ensureNotDeleted();
        ensureCanManage(actor, actorManagesCourse);
        if (this.acceptedAnswerId == null) {
            throw new ConflictException("QA-1026", "该问题没有已采纳的回答");
        }
        this.acceptedAnswerId = null;
        if (this.status == QuestionStatus.RESOLVED) {
            this.status = QuestionStatus.OPEN;
        }
    }

    /** 教师/管理员关闭问题：关闭后不可再回答、采纳。 */
    public void close(Actor actor, boolean actorManagesCourse) {
        ensureNotDeleted();
        if (!actorManagesCourse) {
            throw new ForbiddenException("QA-1029", "只有课程管理者可以关闭问题");
        }
        if (this.status == QuestionStatus.CLOSED) {
            throw new ConflictException("QA-1026", "问题已关闭");
        }
        this.status = QuestionStatus.CLOSED;
    }

    public void softDelete(Actor actor, boolean actorManagesCourse, Instant now) {
        ensureNotDeleted();
        if (!actorManagesCourse) {
            ensureCanManage(actor, false);
            if (answerCount > 0) {
                throw new BusinessRuleException("QA-1012", "问题已有回答，提问者不能删除，请联系课程教师处理");
            }
        }
        this.deletedAt = now;
        this.updatedAt = now;
        this.status = QuestionStatus.DELETED;
    }

    public void ensureOpenForAnswers() {
        ensureNotDeleted();
        ensureOpen();
    }

    private void ensureOpen() {
        if (status == QuestionStatus.CLOSED) {
            throw new BusinessRuleException("QA-1027", "问题已关闭，不能执行该操作");
        }
    }

    private void ensureNotDeleted() {
        if (deletedAt != null) {
            throw new BusinessRuleException("QA-1003", "问题已删除");
        }
    }

    /** 提问者本人或课程管理者之外的操作者一律拒绝（QA-1021）。 */
    private void ensureCanManage(Actor actor, boolean actorManagesCourse) {
        if (!actorManagesCourse && (actor == null || actor.userId() != authorId)) {
            throw new ForbiddenException("QA-1021", "只有提问者本人或课程管理者可以执行该操作");
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 已采纳回答被删除后清空采纳引用，保持"单一事实来源"与读模型一致。 */
    public void clearAcceptedAnswer() {
        this.acceptedAnswerId = null;
        if (this.status == QuestionStatus.RESOLVED) {
            this.status = QuestionStatus.OPEN;
        }
    }

    public boolean isAccepted(long answerId) {
        return acceptedAnswerId != null && acceptedAnswerId == answerId;
    }

    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID 已分配");
        }
        this.id = id;
    }

    public Long id() { return id; }
    public long authorId() { return authorId; }
    public Long courseId() { return courseId; }
    public Title title() { return title; }
    public MarkdownBody body() { return body; }
    public Tags tags() { return tags; }
    public Long acceptedAnswerId() { return acceptedAnswerId; }
    public QuestionStatus status() { return status; }
    public int score() { return score; }
    public int answerCount() { return answerCount; }
    public int viewCount() { return viewCount; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant deletedAt() { return deletedAt; }
    public long version() { return version; }
}
