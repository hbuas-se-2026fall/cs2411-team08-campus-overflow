package com.campusoverflow.qa.application.query;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.application.port.ContentRenderer;
import com.campusoverflow.qa.application.port.QaReadModel;
import com.campusoverflow.qa.application.port.QuestionListCache;
import com.campusoverflow.qa.application.port.ViewCounter;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 问答查询用例（读路径）。 */
@Service
@Transactional(readOnly = true)
public class QuestionQueryService {

    private static final int EXCERPT_LENGTH = 160;
    private static final String COMMENT_TYPE_QUESTION = "QUESTION";
    private static final String COMMENT_TYPE_ANSWER = "ANSWER";

    private final QaReadModel readModel;
    private final IdentityApi identity;
    private final ContentRenderer renderer;
    private final ViewCounter viewCounter;
    private final QuestionListCache cache;

    public QuestionQueryService(QaReadModel readModel, IdentityApi identity, ContentRenderer renderer,
                                ViewCounter viewCounter, QuestionListCache cache) {
        this.readModel = readModel;
        this.identity = identity;
        this.renderer = renderer;
        this.viewCounter = viewCounter;
        this.cache = cache;
    }

    public PageResult<QuestionSummaryView> list(Long courseId, PageRequest page) {
        var cached = cache.get(courseId, page);
        if (cached.isPresent()) {
            return cached.get();
        }
        PageResult<QuestionSummaryView> result = readModel.listQuestions(courseId, page).map(this::toSummary);
        cache.put(courseId, page, result);
        return result;
    }

    public QuestionDetailView detail(long questionId, Actor actorOrNull) {
        QuestionRow question = readModel.findQuestion(questionId)
                .orElseThrow(() -> new NotFoundException("QA-2004", "问题不存在"));
        viewCounter.increment(questionId);

        List<AnswerRow> answerRows = readModel.findAnswers(questionId);
        List<CommentRow> commentRows = readModel.findComments(questionId);
        List<Long> answerIds = answerRows.stream().map(AnswerRow::id).toList();
        Map<Long, UserSummary> users = identity.findUsers(collectAuthorIds(question, answerRows, commentRows));

        boolean actorManagesCourse = actorOrNull != null && question.courseId() != null
                && identity.canManageCourse(actorOrNull.userId(), question.courseId());
        PermissionsView permissions = buildPermissions(question, actorOrNull, actorManagesCourse);
        Map<String, Integer> myVotes = actorOrNull == null ? Map.of()
                : readModel.findMyVotes(actorOrNull.userId(), questionId, answerIds);

        CourseRefView course = question.courseId() == null ? null : identity.findCourse(question.courseId())
                .map(c -> new CourseRefView(c.id(), c.code(), c.name())).orElse(null);

        List<CommentView> questionComments = commentRows.stream()
                .filter(c -> COMMENT_TYPE_QUESTION.equals(c.targetType()))
                .map(c -> toCommentView(c, users))
                .toList();
        List<AnswerView> answers = sortAnswers(answerRows, question.acceptedAnswerId()).stream()
                .map(a -> toAnswerView(a, question.acceptedAnswerId(), commentRows, users, myVotes,
                        actorOrNull, actorManagesCourse))
                .toList();

        return new QuestionDetailView(question.id(), question.title(), question.body(),
                renderer.toSafeHtml(question.body()), question.tags(), course,
                AuthorView.of(question.authorId(), users.get(question.authorId())),
                question.score(), question.answerCount(), question.viewCount(), question.acceptedAnswerId(),
                myVotes.getOrDefault(voteKey(COMMENT_TYPE_QUESTION, questionId), 0),
                question.createdAt(), question.updatedAt(), permissions, questionComments, answers);
    }

    public List<TagView> popularTags(int limit) {
        return readModel.popularTags(limit);
    }

    private QuestionSummaryView toSummary(QuestionRow row) {
        return new QuestionSummaryView(row.id(), row.title(), renderer.toPlainText(row.body(), EXCERPT_LENGTH),
                row.tags(), row.courseId(), AuthorView.of(row.authorId(), identity.findUser(row.authorId())
                        .orElse(null)),
                row.score(), row.answerCount(), row.viewCount(), row.acceptedAnswerId() != null, row.createdAt());
    }

    private AnswerView toAnswerView(AnswerRow row, Long acceptedAnswerId, List<CommentRow> commentRows,
                                    Map<Long, UserSummary> users, Map<String, Integer> myVotes, Actor actorOrNull,
                                    boolean actorManagesCourse) {
        boolean accepted = acceptedAnswerId != null && acceptedAnswerId == row.id();
        boolean canEdit = actorOrNull != null
                && (actorManagesCourse || actorOrNull.userId() == row.authorId());
        AuthorView endorser = row.endorsedBy() == null ? null
                : AuthorView.of(row.endorsedBy(), users.get(row.endorsedBy()));
        List<CommentView> comments = commentRows.stream()
                .filter(c -> COMMENT_TYPE_ANSWER.equals(c.targetType()) && c.targetId() == row.id())
                .map(c -> toCommentView(c, users))
                .toList();
        return new AnswerView(row.id(), row.body(), renderer.toSafeHtml(row.body()),
                AuthorView.of(row.authorId(), users.get(row.authorId())), row.score(), accepted, endorser,
                myVotes.getOrDefault(voteKey(COMMENT_TYPE_ANSWER, row.id()), 0), canEdit,
                row.createdAt(), row.updatedAt(), comments);
    }

    private CommentView toCommentView(CommentRow row, Map<Long, UserSummary> users) {
        return new CommentView(row.id(), AuthorView.of(row.authorId(), users.get(row.authorId())),
                row.parentId(), row.body(), row.createdAt());
    }

    private PermissionsView buildPermissions(QuestionRow question, Actor actor, boolean actorManagesCourse) {
        if (actor == null) {
            return PermissionsView.NONE;
        }
        boolean author = actor.userId() == question.authorId();
        boolean editor = author || actorManagesCourse;
        return new PermissionsView(editor, editor, editor, actorManagesCourse);
    }

    private List<AnswerRow> sortAnswers(List<AnswerRow> rows, Long acceptedAnswerId) {
        // 布尔标志用 reverseOrder：true（采纳/认证）排在前面
        Comparator<AnswerRow> order = Comparator
                .comparing((AnswerRow a) -> acceptedAnswerId != null && acceptedAnswerId == a.id(),
                        Comparator.reverseOrder())
                .thenComparing(a -> a.endorsedBy() != null, Comparator.reverseOrder())
                .thenComparing(AnswerRow::score, Comparator.reverseOrder())
                .thenComparing(AnswerRow::createdAt)
                .thenComparing(AnswerRow::id);
        return rows.stream().sorted(order).toList();
    }

    private Set<Long> collectAuthorIds(QuestionRow question, List<AnswerRow> answers, List<CommentRow> comments) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(question.authorId());
        answers.forEach(a -> {
            ids.add(a.authorId());
            if (a.endorsedBy() != null) {
                ids.add(a.endorsedBy());
            }
        });
        comments.forEach(c -> ids.add(c.authorId()));
        return ids;
    }

    private String voteKey(String type, long targetId) {
        return type + ":" + targetId;
    }
}
