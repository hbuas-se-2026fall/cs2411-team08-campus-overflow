package com.campusoverflow.qa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 回答来源与认证生命周期：编辑使认证失效、撤销认证、只追加留痕的校验。 */
class AnswerEndorsementLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");
    private static final Actor ANSWERER = new Actor(3, "回答者", Role.STUDENT);
    private static final Actor TEACHER = new Actor(9, "王老师", Role.TEACHER);

    private static Answer answer() {
        return QuestionTest.persistedAnswer(QuestionTest.persistedQuestion(), ANSWERER.userId(), 50);
    }

    @Test
    @DisplayName("人工提交默认 HUMAN；旧签名 reconstitute 推导 HUMAN，新签名可携带 AI 来源")
    void sourceIsServerControlled() {
        assertThat(answer().source()).isEqualTo(AnswerSource.HUMAN);

        Answer legacy = Answer.reconstitute(60L, 10L, 3, MarkdownBody.ofAnswer("历史回答内容"), 0, null,
                NOW, NOW, null, 0);
        assertThat(legacy.source()).isEqualTo(AnswerSource.HUMAN);

        Answer ai = Answer.reconstitute(61L, 10L, 3, MarkdownBody.ofAnswer("AI 生成的回答"), AnswerSource.AI,
                0, null, NOW, NOW, null, 0);
        assertThat(ai.source()).isEqualTo(AnswerSource.AI);
    }

    @Test
    @DisplayName("答案正文编辑后当前认证失效（由应用层写 REVOKE 历史并冲正奖励）")
    void editingInvalidatesEndorsement() {
        Answer a = answer();
        a.endorse(TEACHER, true);
        assertThat(a.endorsedBy()).isEqualTo(9L);

        a.edit(ANSWERER, false, MarkdownBody.ofAnswer("补充了更完整的红黑树推导过程"), NOW);
        assertThat(a.endorsedBy()).isNull();
        assertThat(a.body().value()).contains("更完整");
    }

    @Test
    @DisplayName("撤销认证：仅管理者；未认证时冲突；撤销后可重新认证")
    void revocationRules() {
        Answer a = answer();
        assertThatThrownBy(() -> a.revokeEndorsement(ANSWERER, false)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> a.revokeEndorsement(TEACHER, true)).isInstanceOf(ConflictException.class);

        a.endorse(TEACHER, true);
        a.revokeEndorsement(TEACHER, true);
        assertThat(a.endorsedBy()).isNull();

        a.endorse(TEACHER, true);
        assertThat(a.endorsedBy()).isEqualTo(9L);
    }

    @Test
    @DisplayName("认证历史与复核记录：撤销理由必填且长度受限，授予不携带理由")
    void governanceLogsValidateReason() {
        EndorsementLog grant = EndorsementLog.grant(50L, 9L, 3, NOW);
        assertThat(grant.action()).isEqualTo(EndorsementLog.Action.GRANT);
        assertThat(grant.reason()).isNull();

        EndorsementLog revoke = EndorsementLog.revoke(50L, 9L, 3, "答案已过时", NOW);
        assertThat(revoke.action()).isEqualTo(EndorsementLog.Action.REVOKE);
        assertThat(revoke.reason()).isEqualTo("答案已过时");

        assertThatThrownBy(() -> EndorsementLog.revoke(50L, 9L, 3, "  ", NOW))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("撤销原因");
        assertThatThrownBy(() -> EndorsementLog.revoke(50L, 9L, 3, "x".repeat(501), NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> AcceptanceReview.approve(10L, 9L, 0, "", NOW))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("复核理由");
    }
}
