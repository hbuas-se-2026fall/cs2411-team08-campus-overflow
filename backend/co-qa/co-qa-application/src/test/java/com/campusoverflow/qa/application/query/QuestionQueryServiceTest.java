package com.campusoverflow.qa.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingCache;
import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingViewCounter;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryReadModel;
import com.campusoverflow.qa.application.fake.QaTestFixtures.PassthroughRenderer;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 查询服务用例测试：缓存策略、详情装配（排序/权限/我的投票/评论归组）、404。 */
class QuestionQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");

    private final InMemoryReadModel readModel = new InMemoryReadModel();
    private final StubIdentity identity = new StubIdentity()
            .withUser(1, "小明", Role.STUDENT).withUser(2, "小红", Role.STUDENT)
            .withUser(9, "王老师", Role.TEACHER);
    private final CountingCache cache = new CountingCache();
    private final CountingViewCounter viewCounter = new CountingViewCounter();

    private QuestionQueryService service;
    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor anonymous = null;

    @BeforeEach
    void setUp() {
        service = new QuestionQueryService(readModel, identity, new PassthroughRenderer(), viewCounter, cache);
        readModel.questions.add(new QuestionRow(10L, 1L, 100L, "红黑树插入后如何判断旋转方向？",
                "插入节点后出现连续红色节点，什么时候左旋、什么时候右旋？", List.of("数据结构", "红黑树"),
                50L, 4, 3, 7, NOW, NOW));
        readModel.addAnswers(10L,
                new AnswerRow(50L, 2L, "看叔叔节点的颜色和插入位置。", 2, null, NOW, NOW),
                new AnswerRow(51L, 3L, "关键是黑高不变量。", 5, 9L, NOW, NOW),
                new AnswerRow(52L, 4L, "先变色再旋转，顺序不能反。", 10, 9L, NOW, NOW),
                new AnswerRow(53L, 4L, "推荐画一个具体例子。", 10, null, NOW, NOW));
        readModel.addComments(10L,
                new CommentRow(90L, "QUESTION", 10L, 3L, null, "建议补充一个反例。", NOW),
                new CommentRow(91L, "ANSWER", 50L, 1L, null, "请问第二步为什么要先变色？", NOW),
                new CommentRow(92L, "ANSWER", 50L, 2L, 91L, "因为要保持黑高一致。", NOW));
        readModel.vote(1L, "QUESTION:10", 1);
        readModel.vote(1L, "ANSWER:50", -1);
        readModel.vote(1L, "ANSWER:51", 1);
        readModel.tags.add(new TagView("数据结构", 2));
    }

    @Test
    @DisplayName("列表：读模型 → 摘要装配 → 写缓存；二次请求命中缓存不再查读模型")
    void listUsesCacheAside() {
        PageResult<QuestionSummaryView> first = service.list(null, new PageRequest(1, 20));

        assertThat(first.total()).isEqualTo(1);
        QuestionSummaryView summary = first.items().get(0);
        assertThat(summary.excerpt()).doesNotContain("#").hasSizeLessThanOrEqualTo(160);
        assertThat(summary.accepted()).isTrue();
        assertThat(summary.author().displayName()).isEqualTo("小明");
        assertThat(cache.puts).isEqualTo(1);

        PageResult<QuestionSummaryView> cached = new PageResult<>(List.of(), 0, 1, 20);
        cache.hitWith(cached);
        assertThat(service.list(null, new PageRequest(1, 20))).isSameAs(cached);
    }

    @Test
    @DisplayName("详情：回答按 采纳 > 教师认证 > 得分 排序，评论归组到问题与回答")
    void detailAssemblesAnswersCommentsAndPermissions() {
        QuestionDetailView view = service.detail(10L, ming);

        assertThat(view.bodyHtml()).isEqualTo("<p>插入节点后出现连续红色节点，什么时候左旋、什么时候右旋？</p>");
        assertThat(view.myVote()).isEqualTo(1);
        assertThat(view.acceptedAnswerId()).isEqualTo(50L);
        assertThat(view.permissions()).isEqualTo(new PermissionsView(true, true, true, false));
        assertThat(view.comments()).extracting(CommentView::id).containsExactly(90L);

        assertThat(view.answers()).extracting(AnswerView::id)
                .containsExactly(50L, 52L, 51L, 53L); // 采纳 > 认证(得分高先) > 认证 > 未认证
        AnswerView accepted = view.answers().get(0);
        assertThat(accepted.accepted()).isTrue();
        assertThat(accepted.myVote()).isEqualTo(-1);
        assertThat(accepted.endorsedBy()).isNull();
        assertThat(accepted.comments()).extracting(CommentView::id).containsExactly(91L, 92L);

        AnswerView topEndorsed = view.answers().get(1); // 52：认证且得分 10
        assertThat(topEndorsed.endorsedBy().displayName()).isEqualTo("王老师");
        assertThat(topEndorsed.myVote()).isZero();

        AnswerView votedEndorsed = view.answers().get(2); // 51：认证、得分 5，且当前用户投过票
        assertThat(votedEndorsed.myVote()).isEqualTo(1);
        assertThat(viewCounter.increments).containsEntry(10L, 1);
    }

    @Test
    @DisplayName("详情：匿名访问权限为 NONE、我的投票为 0；管理者可以看到管理权限")
    void anonymousAndManagerPermissions() {
        QuestionDetailView anonymousView = service.detail(10L, anonymous);
        assertThat(anonymousView.permissions()).isEqualTo(PermissionsView.NONE);
        assertThat(anonymousView.myVote()).isZero();
        assertThat(anonymousView.answers().get(0).myVote()).isZero();
        assertThat(anonymousView.answers().get(0).canEdit()).isFalse();

        identity.withCourseManager(9L);
        QuestionDetailView teacherView = service.detail(10L, new Actor(9, "王老师", Role.TEACHER));
        assertThat(teacherView.permissions()).isEqualTo(new PermissionsView(true, true, true, true));
        assertThat(teacherView.course()).isNotNull();
        assertThat(teacherView.course().code()).isEqualTo("CS2001");
    }

    @Test
    @DisplayName("详情不存在返回 404；热门标签直通读模型")
    void missingQuestionIsNotFoundAndPopularTagsPassThrough() {
        assertThatThrownBy(() -> service.detail(999L, ming)).isInstanceOf(NotFoundException.class);

        List<TagView> tags = service.popularTags(20);
        assertThat(tags).hasSize(1);
        assertThat(tags.get(0).name()).isEqualTo("数据结构");
    }
}
