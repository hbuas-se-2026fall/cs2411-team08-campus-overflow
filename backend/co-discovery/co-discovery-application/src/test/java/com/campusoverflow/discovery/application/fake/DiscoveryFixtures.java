package com.campusoverflow.discovery.application.fake;

import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationRepository;
import com.campusoverflow.discovery.domain.search.SearchDocument;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex;
import com.campusoverflow.discovery.domain.search.SearchPort;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.shared.event.ProcessedEventStore;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 发现应用层测试夹具：内存仓储与桩。 */
public final class DiscoveryFixtures {

    public static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");

    private DiscoveryFixtures() {
    }

    public static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneId.of("UTC"));
    }

    public static class InMemoryNotifications implements NotificationRepository {
        public final List<Notification> items = new ArrayList<>();
        private long sequence;

        @Override
        public boolean exists(String sourceEventId, long recipientId) {
            return items.stream().anyMatch(n -> n.sourceEventId().equals(sourceEventId)
                    && n.recipientId() == recipientId);
        }

        @Override
        public Notification save(Notification notification) {
            if (notification.id() == null) {
                notification.assignId(++sequence);
                items.add(notification);
            }
            return notification;
        }

        @Override
        public Optional<Notification> findById(long id) {
            return items.stream().filter(n -> n.id() == id).findFirst();
        }

        @Override
        public PageResult<Notification> findByRecipient(long recipientId, PageRequest page) {
            List<Notification> mine = items.stream().filter(n -> n.recipientId() == recipientId).toList();
            return new PageResult<>(mine, mine.size(), page.page(), page.size());
        }

        @Override
        public long countUnread(long recipientId) {
            return items.stream().filter(n -> n.recipientId() == recipientId && !n.read()).count();
        }

        @Override
        public int markAllRead(long recipientId) {
            int updated = 0;
            for (Notification notification : items) {
                if (notification.recipientId() == recipientId && !notification.read()) {
                    notification.markRead(recipientId);
                    updated++;
                }
            }
            return updated;
        }
    }

    public static class InMemorySearchIndex implements SearchIndex {
        public final Map<Long, Doc> docs = new LinkedHashMap<>();
        public final List<String> actions = new ArrayList<>();
        public List<RelatedCandidate> candidates = List.of();

        public static class Doc {
            public String title;
            public String body;
            public List<String> tags = List.of();
            public int score;
            public int answerCount;
            public boolean accepted;
            public boolean bountyOpen;
            public boolean deleted;
        }

        @Override
        public void insert(SearchDocument document) {
            actions.add("insert:" + document.questionId());
            Doc doc = new Doc();
            doc.title = document.title();
            doc.body = document.bodyText();
            doc.tags = document.tags();
            docs.put(document.questionId(), doc);
        }

        @Override
        public void updateText(long questionId, String title, String bodyText, List<String> tags, Instant at) {
            actions.add("updateText:" + questionId);
            Doc doc = docs.get(questionId);
            if (doc != null) {
                doc.title = title;
                doc.body = bodyText;
                doc.tags = tags;
            }
        }

        @Override
        public void adjustAnswerCount(long questionId, int delta, Instant at) {
            actions.add("answerCount:" + questionId + ":" + delta);
            Doc doc = docs.get(questionId);
            if (doc != null) {
                doc.answerCount += delta;
            }
        }

        @Override
        public void adjustScore(long questionId, int delta) {
            actions.add("score:" + questionId + ":" + delta);
            Doc doc = docs.get(questionId);
            if (doc != null) {
                doc.score += delta;
            }
        }

        @Override
        public void markAccepted(long questionId, Instant at) {
            actions.add("accepted:" + questionId);
            Doc doc = docs.get(questionId);
            if (doc != null) {
                doc.accepted = true;
            }
        }

        @Override
        public void setBountyOpen(long questionId, boolean open) {
            actions.add("bounty:" + questionId + ":" + open);
            Doc doc = docs.get(questionId);
            if (doc != null) {
                doc.bountyOpen = open;
            }
        }

        @Override
        public void markDeleted(long questionId) {
            actions.add("deleted:" + questionId);
            Doc doc = docs.get(questionId);
            if (doc != null) {
                doc.deleted = true;
            }
        }

        @Override
        public List<String> findTags(long questionId) {
            Doc doc = docs.get(questionId);
            return doc == null ? List.of() : doc.tags;
        }

        @Override
        public List<RelatedCandidate> findCandidatesSharingTags(long questionId, List<String> tags, int limit) {
            return candidates;
        }
    }

    public static class StubSearchPort implements SearchPort {
        private final List<SearchHit> hits = new ArrayList<>();

        public StubSearchPort hit(SearchHit hit) {
            hits.add(hit);
            return this;
        }

        @Override
        public PageResult<SearchHit> search(SearchQuery query) {
            return new PageResult<>(hits, hits.size(), query.page().page(), query.page().size());
        }
    }

    public static class StubIdentity implements IdentityApi {
        private final Map<Long, UserSummary> users = new HashMap<>();

        public StubIdentity user(long id, String name, Role role) {
            users.put(id, new UserSummary(id, name, role, true, "计算机工程学院"));
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
            return Optional.of(new CourseSummary(courseId, "CS2001", "数据结构", "2026-秋", "OPEN", 100L));
        }

        @Override
        public boolean canManageCourse(long userId, Long courseId) {
            return false;
        }

        @Override
        public boolean isCourseMember(long userId, long courseId) {
            return true;
        }
    }

    public static class StubQa implements QaQueryApi {
        @Override
        public Optional<QuestionRef> findQuestion(long questionId) {
            return Optional.empty();
        }
    }

    public static class InMemoryProcessed implements ProcessedEventStore {
        private final Set<String> keys = new HashSet<>();

        @Override
        public boolean markProcessed(String eventId, String consumer) {
            return keys.add(consumer + ":" + eventId);
        }
    }
}
