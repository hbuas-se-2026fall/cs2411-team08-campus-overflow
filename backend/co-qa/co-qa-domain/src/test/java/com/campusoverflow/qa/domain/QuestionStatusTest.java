package com.campusoverflow.qa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 问题状态机：采纳只建议 RESOLVED，治理状态 CLOSED 不被覆盖。 */
class QuestionStatusTest {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");
    private static final Actor ASKER = new Actor(1, "提问者", Role.STUDENT);
    private static final Actor STRANGER = new Actor(77, "路人", Role.STUDENT);
    private static final Actor TEACHER = new Actor(9, "王老师", Role.TEACHER);

    private static Question openQuestion() {
        return QuestionTest.persistedQuestion();
    }

    @Test
    @DisplayName("采纳把 OPEN 建议为 RESOLVED；撤销采纳回到 OPEN")
    void acceptAndRevokeTransition() {
        Question q = openQuestion();
        Answer a = QuestionTest.persistedAnswer(q, 2, 50);

        q.accept(a, ASKER, false);
        assertThat(q.status()).isEqualTo(QuestionStatus.RESOLVED);
        assertThat(q.acceptedAnswerId()).isEqualTo(50L);

        q.revokeAcceptance(ASKER, false);
        assertThat(q.status()).isEqualTo(QuestionStatus.OPEN);
        assertThat(q.acceptedAnswerId()).isNull();
    }

    @Test
    @DisplayName("无采纳不可撤销；路人与普通学生不能撤销")
    void revokeRequiresAcceptanceAndPermission() {
        Question q = openQuestion();
        assertThatThrownBy(() -> q.revokeAcceptance(ASKER, false)).isInstanceOf(ConflictException.class);

        Answer a = QuestionTest.persistedAnswer(q, 2, 50);
        q.accept(a, ASKER, false);
        assertThatThrownBy(() -> q.revokeAcceptance(STRANGER, false)).isInstanceOf(ForbiddenException.class);
        q.revokeAcceptance(TEACHER, true);
        assertThat(q.acceptedAnswerId()).isNull();
    }

    @Test
    @DisplayName("关闭问题：仅课程管理者；关闭后不可采纳、不可回答")
    void closedQuestionRejectsAnsweringAndAccepting() {
        Question q = openQuestion();
        Answer a = QuestionTest.persistedAnswer(q, 2, 50);
        assertThatThrownBy(() -> q.close(ASKER, false)).isInstanceOf(ForbiddenException.class);

        q.close(TEACHER, true);
        assertThat(q.status()).isEqualTo(QuestionStatus.CLOSED);
        assertThatThrownBy(() -> q.close(TEACHER, true)).isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> q.accept(a, ASKER, false)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("关闭");
        assertThatThrownBy(() -> Answer.submit(q, 2, MarkdownBody.ofAnswer("一个迟到的回答内容"), NOW))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("关闭");
    }

    @Test
    @DisplayName("撤销采纳不覆盖 CLOSED 治理状态")
    void revokeKeepsGovernanceStatus() {
        Question closed = Question.reconstitute(10L, ASKER.userId(), 100L,
                new Title("一个已被教师关闭但还挂着采纳答案的问题"),
                MarkdownBody.ofQuestion("关闭后撤销采纳，状态应仍然是 CLOSED。"), Tags.of(List.of("java")),
                50L, QuestionStatus.CLOSED, 0, 1, 0, NOW, NOW, null, 0);

        closed.revokeAcceptance(ASKER, false);
        assertThat(closed.acceptedAnswerId()).isNull();
        assertThat(closed.status()).isEqualTo(QuestionStatus.CLOSED);
    }

    @Test
    @DisplayName("软删除置 DELETED；旧签名 reconstitute 按软删除/采纳推导状态")
    void softDeleteAndReconstituteDeriveStatus() {
        Question q = openQuestion();
        q.softDelete(ASKER, false, NOW);
        assertThat(q.status()).isEqualTo(QuestionStatus.DELETED);

        Question deleted = Question.reconstitute(10L, ASKER.userId(), 100L,
                new Title("一个已经软删除的问题标题内容"),
                MarkdownBody.ofQuestion("这个问题已经被删除了。"), Tags.of(List.of("java")),
                null, 0, 0, 0, NOW, NOW, NOW, 0);
        assertThat(deleted.status()).isEqualTo(QuestionStatus.DELETED);

        Question resolved = Question.reconstitute(11L, ASKER.userId(), 100L,
                new Title("一个已经有采纳答案的问题标题"),
                MarkdownBody.ofQuestion("这个问题已经有了采纳答案。"), Tags.of(List.of("java")),
                50L, 0, 1, 0, NOW, NOW, null, 0);
        assertThat(resolved.status()).isEqualTo(QuestionStatus.RESOLVED);
    }
}
