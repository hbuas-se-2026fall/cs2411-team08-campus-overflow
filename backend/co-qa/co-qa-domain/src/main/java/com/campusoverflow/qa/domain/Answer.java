package com.campusoverflow.qa.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import java.time.Instant;
import java.util.Objects;

/**
 * 回答聚合根。独立于 Question 聚合以降低并发冲突，通过 questionId 引用问题。
 * "是否被采纳"由 Question.acceptedAnswerId 决定（单一事实来源），这里不重复存储。
 * 认证绑定到答案内容版本：正文一旦被编辑，当前认证即失效，由应用层写历史并发布冲正事件。
 */
public class Answer {

    private Long id;
    private final long questionId;
    private final long authorId;
    private MarkdownBody body;
    private final AnswerSource source;
    private final int score;
    private Long endorsedBy;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private final long version;

    private Answer(Long id, long questionId, long authorId, MarkdownBody body, AnswerSource source, int score,
                   Long endorsedBy, Instant createdAt, Instant updatedAt, Instant deletedAt, long version) {
        this.id = id;
        this.questionId = questionId;
        this.authorId = authorId;
        this.body = Objects.requireNonNull(body);
        this.source = Objects.requireNonNull(source);
        this.score = score;
        this.endorsedBy = endorsedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.version = version;
    }

    public static Answer submit(Question question, long authorId, MarkdownBody body, Instant now) {
        Objects.requireNonNull(question, "question 不能为空");
        question.ensureOpenForAnswers();
        return new Answer(null, question.id(), authorId, body, AnswerSource.HUMAN, 0, null, now, null, null, 0);
    }

    /** 旧调用点兼容：历史数据一律视为人工回答。 */
    public static Answer reconstitute(Long id, long questionId, long authorId, MarkdownBody body, int score,
                                      Long endorsedBy, Instant createdAt, Instant updatedAt, Instant deletedAt,
                                      long version) {
        return reconstitute(id, questionId, authorId, body, AnswerSource.HUMAN, score, endorsedBy, createdAt,
                updatedAt, deletedAt, version);
    }

    public static Answer reconstitute(Long id, long questionId, long authorId, MarkdownBody body, AnswerSource source,
                                      int score, Long endorsedBy, Instant createdAt, Instant updatedAt,
                                      Instant deletedAt, long version) {
        return new Answer(id, questionId, authorId, body, source, score, endorsedBy, createdAt, updatedAt,
                deletedAt, version);
    }

    /** 编辑后当前认证失效（由应用层据此写 REVOKE 历史并发冲正事件）。 */
    public void edit(Actor actor, boolean actorManagesCourse, MarkdownBody newBody, Instant now) {
        ensureNotDeleted();
        ensureCanManage(actor, actorManagesCourse);
        this.body = Objects.requireNonNull(newBody);
        this.updatedAt = now;
        this.endorsedBy = null;
    }

    /** 教师认证：课程教师/助教为优质回答打上"教师认证"标记。 */
    public void endorse(Actor actor, boolean actorManagesCourse) {
        ensureNotDeleted();
        if (!actorManagesCourse) {
            throw new ForbiddenException("QA-1022", "只有课程管理者可以认证回答");
        }
        if (this.endorsedBy != null) {
            throw new ConflictException("QA-1023", "该回答已被认证，且认证后不可重复认证");
        }
        this.endorsedBy = actor.userId();
    }

    /** 撤销认证：仅课程管理者；未认证时撤销无意义。 */
    public void revokeEndorsement(Actor actor, boolean actorManagesCourse) {
        ensureNotDeleted();
        if (!actorManagesCourse) {
            throw new ForbiddenException("QA-1022", "只有课程管理者可以撤销认证");
        }
        if (this.endorsedBy == null) {
            throw new ConflictException("QA-1028", "该回答当前没有有效认证");
        }
        this.endorsedBy = null;
    }

    /** 被采纳的回答不能由回答者本人删除（避免"先骗采纳再删除"），课程管理者可删除。 */
    public void softDelete(Actor actor, boolean actorManagesCourse, boolean accepted, Instant now) {
        ensureNotDeleted();
        if (!actorManagesCourse) {
            if (accepted) {
                throw new BusinessRuleException("QA-1013", "已采纳的回答不能由回答者本人删除，请联系课程教师处理");
            }
            ensureCanManage(actor, false);
        }
        this.deletedAt = now;
        this.updatedAt = now;
    }

    private void ensureNotDeleted() {
        if (deletedAt != null) {
            throw new BusinessRuleException("QA-1016", "回答已删除");
        }
    }

    /** 回答者本人或课程管理者之外的操作者一律拒绝（QA-1024）。 */
    private void ensureCanManage(Actor actor, boolean actorManagesCourse) {
        if (!actorManagesCourse && (actor == null || actor.userId() != authorId)) {
            throw new ForbiddenException("QA-1024", "只有回答者本人或课程管理者可以执行该操作");
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID 已分配");
        }
        this.id = id;
    }

    public Long id() { return id; }
    public long questionId() { return questionId; }
    public long authorId() { return authorId; }
    public MarkdownBody body() { return body; }
    public AnswerSource source() { return source; }
    public int score() { return score; }
    public Long endorsedBy() { return endorsedBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant deletedAt() { return deletedAt; }
    public long version() { return version; }
}
