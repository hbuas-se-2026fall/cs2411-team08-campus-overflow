package com.campusoverflow.qa.application.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingCache;
import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingViewCounter;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryReadModel;
import com.campusoverflow.qa.application.fake.QaTestFixtures.PassthroughRenderer;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 详情回答分页与治理能力标记；两参旧重载保持默认整页行为。 */
class QuestionDetailPagingTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");

    private final InMemoryReadModel readModel = new InMemoryReadModel();
    private final StubIdentity identity = new StubIdentity()
            .withUser(1, "小明", Role.STUDENT).withUser(2, "小红", Role.STUDENT)
            .withUser(9, "王老师", Role.TEACHER);
    private final CountingCache cache = new CountingCache();
    private final CountingViewCounter viewCounter = new CountingViewCounter();
    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor teacher = new Actor(9, "王老师", Role.TEACHER);

    private QuestionQueryService service;

    @BeforeEach
    void setUp() {
        service = new QuestionQueryService(readModel, identity, new PassthroughRenderer(), viewCounter, cache);
        readModel.questions.add(new QuestionRow(10L, 1L, 100L, "红黑树插入后如何判断旋转方向？",
                "插入节点后出现连续红色节点，什么时候左旋、什么时候右旋？", List.of("数据结构", "红黑树"),
                50L, 4, 4, 7, NOW, NOW, "RESOLVED"));
        readModel.addAnswers(10L,
                new AnswerRow(50L, 2L, "看叔叔节点的颜色和插入位置。", 2, null, NOW, NOW),
                new AnswerRow(51L, 3L, "关键是黑高不变量。", 5, 9L, NOW, NOW),
                new AnswerRow(52L, 4L, "先变色再旋转，顺序不能反。", 10, 9L, NOW, NOW),
                new AnswerRow(53L, 4L, "推荐画一个具体例子。", 10, null, NOW, NOW));
        identity.withCourseManager(9L);
    }

    @Test
    @DisplayName("回答分页：按页截取，size 上限由 PageRequest 夹到 50；旧两参重载默认第一页")
    void answersArePaged() {
        assertThat(service.detail(10L, ming, 1, 2).answers()).hasSize(2);
        assertThat(service.detail(10L, ming, 2, 2).answers()).hasSize(2);
        assertThat(service.detail(10L, ming, 3, 2).answers()).isEmpty();
        assertThat(service.detail(10L, ming, 1, 99).answers()).hasSize(4);
        assertThat(service.detail(10L, ming).answers()).hasSize(4);
    }

    @Test
    @DisplayName("详情携带问题状态、来源与细分治理能力标记")
    void detailCarriesStatusSourceAndGovernanceFlags() {
        QuestionDetailView studentView = service.detail(10L, ming);
        assertThat(studentView.status()).isEqualTo("RESOLVED");
        assertThat(studentView.canReview()).isFalse();
        assertThat(studentView.canEndorse()).isFalse();
        assertThat(studentView.answers()).allSatisfy(a -> assertThat(a.source()).isEqualTo("HUMAN"));

        QuestionDetailView teacherView = service.detail(10L, teacher);
        assertThat(teacherView.canReview()).isTrue();
        assertThat(teacherView.canEndorse()).isTrue();
        assertThat(teacherView.answerCount()).isEqualTo(4);
    }
}
