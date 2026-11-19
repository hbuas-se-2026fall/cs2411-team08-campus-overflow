package com.campusoverflow.qa.application.fake;

import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.application.port.ContentRenderer;
import com.campusoverflow.qa.application.port.QuestionListCache;
import com.campusoverflow.qa.application.port.QaReadModel;
import com.campusoverflow.qa.application.port.ViewCounter;
import com.campusoverflow.qa.application.query.AnswerRow;
import com.campusoverflow.qa.application.query.CommentRow;
import com.campusoverflow.qa.application.query.QuestionRow;
import com.campusoverflow.qa.application.query.QuestionSummaryView;
import com.campusoverflow.qa.application.query.TagView;
import com.campusoverflow.qa.domain.AcceptanceReview;
import com.campusoverflow.qa.domain.AcceptanceReviewRepository;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.AnswerRepository;
import com.campusoverflow.qa.domain.EndorsementLog;
import com.campusoverflow.qa.domain.EndorsementLogRepository;
import com.campusoverflow.qa.domain.Comment;
import com.campusoverflow.qa.domain.CommentRepository;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.QuestionRepository;
import com.campusoverflow.qa.domain.TagRepository;
import com.campusoverflow.qa.domain.TargetType;
import com.campusoverflow.qa.domain.Vote;
import com.campusoverflow.qa.domain.VoteRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.event.IntegrationEvent;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Role;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 手写测试替身（Test Double）。应用服务只依赖端口接口，因此不需要 Spring 容器、不需要数据库，
 * 单元测试可以在毫秒级完成——这正是“可测试性”作为驱动性架构特征带来的收益。
 */
public final class QaTestFixtures {

    private QaTestFixtures() {
    }

    public static class InMemoryQuestions implements QuestionRepository {
        private final Map<Long, Question> store = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong();
        public final Map<Long, Integer> answerCountDeltas = new HashMap<>();
        public final Map<Long, Integer> scoreDeltas = new HashMap<>();

        @Override
        public Optional<Question> findById(long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Question save(Question question) {
            if (question.id() == null) {
                question.assignId(sequence.incrementAndGet());
            }
            store.put(question.id(), question);
            return question;
        }

        @Override
        public void adjustAnswerCount(long questionId, int delta) {
            answerCountDeltas.merge(questionId, delta, Integer::sum);
        }

        @Override
        public void adjustScore(long questionId, int delta) {
            scoreDeltas.merge(questionId, delta, Integer::sum);
        }
    }

    public static class InMemoryAnswers implements AnswerRepository {
        private final Map<Long, Answer> store = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong();
        public final Map<Long, Integer> scoreDeltas = new HashMap<>();

        @Override
        public Optional<Answer> findById(long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Answer save(Answer answer) {
            if (answer.id() == null) {
                answer.assignId(sequence.incrementAndGet());
            }
            store.put(answer.id(), answer);
            return answer;
        }

        @Override
        public void adjustScore(long answerId, int delta) {
            scoreDeltas.merge(answerId, delta, Integer::sum);
        }
    }

    public static class InMemoryAcceptanceReviews implements AcceptanceReviewRepository {
        public final List<AcceptanceReview> saved = new ArrayList<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public void save(AcceptanceReview review) {
            review.assignId(sequence.incrementAndGet());
            saved.add(review);
        }

        @Override
        public List<AcceptanceReview> findByQuestion(long questionId) {
            return saved.stream().filter(r -> r.questionId() == questionId).toList();
        }
    }

    public static class InMemoryEndorsementLogs implements EndorsementLogRepository {
        public final List<EndorsementLog> saved = new ArrayList<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public void save(EndorsementLog log) {
            log.assignId(sequence.incrementAndGet());
            saved.add(log);
        }

        @Override
        public List<EndorsementLog> findByAnswer(long answerId) {
            return saved.stream().filter(l -> l.answerId() == answerId).toList();
        }
    }

    public static class InMemoryVotes implements VoteRepository {        private final Map<String, Vote> store = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public Optional<Vote> find(long voterId, TargetType targetType, long targetId) {
            return Optional.ofNullable(store.get(key(voterId, targetType, targetId)));
        }

        @Override
        public Vote save(Vote vote) {
            if (vote.id() == null) {
                vote.assignId(sequence.incrementAndGet());
            }
            store.put(key(vote.voterId(), vote.targetType(), vote.targetId()), vote);
            return vote;
        }

        @Override
        public void delete(Vote vote) {
            store.remove(key(vote.voterId(), vote.targetType(), vote.targetId()));
        }

        private static String key(long voterId, TargetType type, long targetId) {
            return voterId + ":" + type + ":" + targetId;
        }
    }

    public static class InMemoryComments implements CommentRepository {
        private final Map<Long, Comment> store = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public Optional<Comment> findById(long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Comment save(Comment comment) {
            if (comment.id() == null) {
                comment.assignId(sequence.incrementAndGet());
            }
            store.put(comment.id(), comment);
            return comment;
        }
    }

    /** 记录标签使用计数调用，便于断言“发布 +1 / 删除 -1”。 */
    public static class RecordingTags implements TagRepository {
        public final List<String> calls = new ArrayList<>();

        @Override
        public void adjustUsage(Collection<String> tagNames, int delta) {
            calls.add(delta + ":" + String.join(",", tagNames));
        }

        public int netDeltaOf(String tag) {
            return calls.stream().mapToInt(c -> {
                int colon = c.indexOf(':');
                int delta = Integer.parseInt(c.substring(0, colon));
                boolean contains = List.of(c.substring(colon + 1).split(",")).contains(tag);
                return contains ? delta : 0;
            }).sum();
        }
    }

    /** 记录所有已发布事件，便于断言“领域动作 → 集成事件”的契约。 */
    public static class RecordingEvents implements IntegrationEventPublisher {
        public final List<IntegrationEvent> published = new ArrayList<>();

        @Override
        public void publish(IntegrationEvent event) {
            published.add(event);
        }

        public <T extends IntegrationEvent> List<T> ofType(Class<T> type) {
            return published.stream().filter(type::isInstance).map(type::cast).collect(Collectors.toList());
        }
    }

    public static class StubIdentity implements IdentityApi {
        private final Map<Long, UserSummary> users = new HashMap<>();
        private final Map<Long, Boolean> courseManagers = new HashMap<>();
        private final Map<Long, CourseSummary> courses = new HashMap<>();
        private final Set<Long> nonMembers = new HashSet<>();

        public StubIdentity withUser(long id, String name, Role role) {
            users.put(id, new UserSummary(id, name, role, true, "计算机工程学院"));
            return this;
        }

        public StubIdentity withCourseManager(long userId) {
            courseManagers.put(userId, true);
            return this;
        }

        /** 覆盖默认课程（默认任意课程均为 OPEN）。 */
        public StubIdentity withCourse(long id, String status) {
            courses.put(id, new CourseSummary(id, "CS2001", "数据结构", "2026-秋", status, 100L));
            return this;
        }

        public StubIdentity excludeFromCourse(long userId) {
            nonMembers.add(userId);
            return this;
        }

        @Override
        public Optional<UserSummary> findUser(long userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public Map<Long, UserSummary> findUsers(Collection<Long> userIds) {
            Map<Long, UserSummary> result = new HashMap<>();
            userIds.forEach(id -> findUser(id).ifPresent(u -> result.put(id, u)));
            return result;
        }

        @Override
        public Optional<UserSummary> findByDisplayName(String displayName) {
            return users.values().stream().filter(u -> u.displayName().equals(displayName)).findFirst();
        }

        @Override
        public Optional<CourseSummary> findCourse(long courseId) {
            if (courses.containsKey(courseId)) {
                return Optional.of(courses.get(courseId));
            }
            return Optional.of(new CourseSummary(courseId, "CS2001", "数据结构", "2026-秋", "OPEN", 100L));
        }

        @Override
        public boolean isCourseMember(long userId, long courseId) {
            // 问答测试默认全员为课程成员；成员资格校验在 Identity 上下文有自己的领域测试
            return !nonMembers.contains(userId);
        }

        @Override
        public boolean canManageCourse(long userId, Long courseId) {
            return courseManagers.getOrDefault(userId, false);
        }
    }

    public static class CountingCache implements QuestionListCache {
        public int invalidations;
        public int puts;
        public PageResult<QuestionSummaryView> lastPut;
        private PageResult<QuestionSummaryView> nextHit;

        /** 预置下一次 get 的命中结果（模拟缓存命中）。 */
        public CountingCache hitWith(PageResult<QuestionSummaryView> value) {
            this.nextHit = value;
            return this;
        }

        @Override
        public Optional<PageResult<QuestionSummaryView>> get(Long courseId, PageRequest page) {
            return Optional.ofNullable(nextHit);
        }

        @Override
        public void put(Long courseId, PageRequest page, PageResult<QuestionSummaryView> value) {
            puts++;
            lastPut = value;
        }

        @Override
        public void invalidateAll() {
            invalidations++;
        }
    }

    public static class RecordingAudit implements AuditTrail {
        public final List<String> actions = new ArrayList<>();

        @Override
        public void record(String action, Long actorId, String targetType, String targetId, String detail) {
            actions.add(action);
        }
    }

    /** 直通渲染器：不引入 Markdown 依赖，仅保证内容可区分。 */
    public static class PassthroughRenderer implements ContentRenderer {
        @Override
        public String toSafeHtml(String markdown) {
            return "<p>" + markdown + "</p>";
        }

        @Override
        public String toPlainText(String markdown, int maxLength) {
            String plain = markdown.replaceAll("[#*`>\\[\\]]", "").strip();
            return plain.length() <= maxLength ? plain : plain.substring(0, maxLength);
        }
    }

    public static class CountingViewCounter implements ViewCounter {
        public final Map<Long, Integer> increments = new HashMap<>();

        @Override
        public void increment(long questionId) {
            increments.merge(questionId, 1, Integer::sum);
        }
    }

    /** 内存读模型：AnswerRow/CommentRow 不携带 questionId，内部按 questionId 分组存放。 */
    public static class InMemoryReadModel implements QaReadModel {
        public final List<QuestionRow> questions = new ArrayList<>();
        public final Map<Long, List<AnswerRow>> answersByQuestion = new LinkedHashMap<>();
        public final Map<Long, List<CommentRow>> commentsByQuestion = new LinkedHashMap<>();
        public final Map<Long, Map<String, Integer>> votesByVoter = new HashMap<>();
        public final List<TagView> tags = new ArrayList<>();

        public void addAnswers(long questionId, AnswerRow... rows) {
            answersByQuestion.computeIfAbsent(questionId, k -> new ArrayList<>()).addAll(List.of(rows));
        }

        public void addComments(long questionId, CommentRow... rows) {
            commentsByQuestion.computeIfAbsent(questionId, k -> new ArrayList<>()).addAll(List.of(rows));
        }

        public void vote(long voterId, String key, int value) {
            votesByVoter.computeIfAbsent(voterId, k -> new HashMap<>()).put(key, value);
        }

        @Override
        public PageResult<QuestionRow> listQuestions(Long courseId, PageRequest page) {
            List<QuestionRow> filtered = questions.stream()
                    .filter(q -> courseId == null || courseId.equals(q.courseId())).toList();
            int from = (int) Math.min(filtered.size(), page.offset());
            int to = (int) Math.min(filtered.size(), (long) page.offset() + page.size());
            return new PageResult<>(filtered.subList(from, to), filtered.size(), page.page(), page.size());
        }

        @Override
        public Optional<QuestionRow> findQuestion(long questionId) {
            return questions.stream().filter(q -> q.id() == questionId).findFirst();
        }

        @Override
        public PageResult<AnswerRow> findAnswers(long questionId, PageRequest page) {
            List<AnswerRow> all = answersByQuestion.getOrDefault(questionId, List.of());
            int from = (int) Math.min(all.size(), page.offset());
            int to = (int) Math.min(all.size(), (long) page.offset() + page.size());
            return new PageResult<>(all.subList(from, to), all.size(), page.page(), page.size());
        }

        @Override
        public List<CommentRow> findComments(long questionId) {
            return commentsByQuestion.getOrDefault(questionId, List.of());
        }

        @Override
        public Map<String, Integer> findMyVotes(long voterId, long questionId, Collection<Long> answerIds) {
            Map<String, Integer> all = votesByVoter.getOrDefault(voterId, Map.of());
            Map<String, Integer> result = new HashMap<>();
            result.put("QUESTION:" + questionId, all.getOrDefault("QUESTION:" + questionId, 0));
            answerIds.forEach(id -> result.put("ANSWER:" + id, all.getOrDefault("ANSWER:" + id, 0)));
            return result;
        }

        @Override
        public List<TagView> popularTags(int limit) {
            return tags.stream().limit(limit).toList();
        }
    }
}
