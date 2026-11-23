package com.campusoverflow.reputation.application.fake;

import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.reputation.application.LeaderboardPeriod;
import com.campusoverflow.reputation.application.LeaderboardStore;
import com.campusoverflow.reputation.domain.BadgeAward;
import com.campusoverflow.reputation.domain.BadgeRepository;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.reputation.domain.Bounty;
import com.campusoverflow.reputation.domain.BountyRepository;
import com.campusoverflow.reputation.domain.BountyStatus;
import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.event.IntegrationEvent;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.event.ProcessedEventStore;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 声誉应用层测试夹具：内存仓储与桩。 */
public final class ReputationFixtures {

    public static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");

    private ReputationFixtures() {
    }

    public static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneId.of("UTC"));
    }

    public static class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        public MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        public void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new MutableClock(instant, newZone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    public static class InMemoryAccounts implements ReputationAccountRepository {
        private final Map<Long, ReputationAccount> store = new LinkedHashMap<>();

        @Override
        public Optional<ReputationAccount> findByUserId(long userId) {
            return Optional.ofNullable(store.get(userId));
        }

        @Override
        public List<ReputationAccount> findAllByUserId(Collection<Long> userIds) {
            return userIds.stream().map(store::get).filter(Objects::nonNull).toList();
        }

        @Override
        public List<ReputationAccount> topByReputation(int limit) {
            return store.values().stream()
                    .sorted(Comparator.comparingInt(ReputationAccount::reputation).reversed()
                            .thenComparingLong(ReputationAccount::userId))
                    .limit(limit).toList();
        }

        @Override
        public List<ReputationAccount> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public ReputationAccount save(ReputationAccount account) {
            store.put(account.userId(), account);
            return account;
        }

        public ReputationAccount get(long userId) {
            return store.get(userId);
        }
    }

    public static class InMemoryLedger implements LedgerRepository {
        public final List<LedgerEntry> entries = new ArrayList<>();

        @Override
        public boolean exists(String eventId, long userId, ReputationReason reason) {
            return entries.stream().anyMatch(e -> e.eventId().equals(eventId) && e.userId() == userId
                    && e.reason() == reason);
        }

        @Override
        public void append(LedgerEntry entry) {
            entries.add(new LedgerEntry((long) (entries.size() + 1), entry.userId(), entry.eventId(), entry.reason(),
                    entry.delta(), entry.refType(), entry.refId(), entry.createdAt()));
        }

        @Override
        public PageResult<LedgerEntry> findByUser(long userId, PageRequest page) {
            List<LedgerEntry> mine = entries.stream().filter(e -> e.userId() == userId).toList();
            return new PageResult<>(mine, mine.size(), page.page(), page.size());
        }

        @Override
        public long countByUserAndReason(long userId, ReputationReason reason) {
            return entries.stream().filter(e -> e.userId() == userId && e.reason() == reason).count();
        }

        @Override
        public int sumPositiveVoteDeltaSince(long userId, Instant since) {
            return entries.stream()
                    .filter(e -> e.userId() == userId && e.reason().isVote() && e.delta() > 0
                            && !e.createdAt().isBefore(since))
                    .mapToInt(LedgerEntry::delta).sum();
        }

        @Override
        public Map<Long, Long> sumByUser() {
            Map<Long, Long> result = new HashMap<>();
            entries.forEach(e -> result.merge(e.userId(), (long) e.delta(), Long::sum));
            return result;
        }
    }

    public static class InMemoryBounties implements BountyRepository {
        private final Map<Long, Bounty> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<Bounty> findOpenByQuestion(long questionId) {
            return store.values().stream()
                    .filter(b -> b.questionId() == questionId && b.status() == BountyStatus.OPEN)
                    .findFirst();
        }

        @Override
        public Optional<Bounty> findLatestByQuestion(long questionId) {
            return store.values().stream().filter(b -> b.questionId() == questionId).reduce((a, b) -> b);
        }

        @Override
        public List<Bounty> findDue(Instant now, int limit) {
            return store.values().stream()
                    .filter(b -> b.status() == BountyStatus.OPEN && !now.isBefore(b.expiresAt()))
                    .limit(limit).toList();
        }

        @Override
        public Bounty save(Bounty bounty) {
            if (bounty.id() == null) {
                bounty.assignId(++sequence);
            }
            store.put(bounty.id(), bounty);
            return bounty;
        }
    }

    public static class InMemoryBadges implements BadgeRepository {
        private final Set<String> keys = new LinkedHashSet<>();
        public final List<BadgeAward> awards = new ArrayList<>();

        @Override
        public boolean has(long userId, BadgeType type) {
            return keys.contains(userId + ":" + type);
        }

        @Override
        public void award(BadgeAward award) {
            keys.add(award.userId() + ":" + award.type());
            awards.add(award);
        }

        @Override
        public List<BadgeAward> findByUser(long userId) {
            return awards.stream().filter(a -> a.userId() == userId).toList();
        }
    }

    public static class RecordingEvents implements IntegrationEventPublisher {
        public final List<IntegrationEvent> published = new ArrayList<>();

        @Override
        public void publish(IntegrationEvent event) {
            published.add(event);
        }

        public <T> List<T> ofType(Class<T> type) {
            return published.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }

    public static class StubIdentity implements IdentityApi {
        private final Map<Long, UserSummary> users = new HashMap<>();
        private final Map<Long, Boolean> managers = new HashMap<>();

        public StubIdentity user(long id, String name, Role role) {
            users.put(id, new UserSummary(id, name, role, true, "计算机工程学院"));
            return this;
        }

        public StubIdentity manager(long userId) {
            managers.put(userId, true);
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
            return managers.getOrDefault(userId, false);
        }

        @Override
        public boolean isCourseMember(long userId, long courseId) {
            return true;
        }
    }

    public static class StubQa implements QaQueryApi {
        private final Map<Long, QuestionRef> questions = new HashMap<>();

        public StubQa question(long id, long authorId, Long courseId, Long acceptedAnswerId, boolean deleted) {
            questions.put(id, new QuestionRef(id, authorId, courseId, "问题" + id, acceptedAnswerId, deleted));
            return this;
        }

        @Override
        public Optional<QuestionRef> findQuestion(long questionId) {
            return Optional.ofNullable(questions.get(questionId));
        }
    }

    public static class RecordingAudit implements AuditTrail {
        public final List<String> actions = new ArrayList<>();

        @Override
        public void record(String action, Long actorId, String targetType, String targetId, String detail) {
            actions.add(action);
        }
    }

    public static class InMemoryLeaderboard implements LeaderboardStore {
        public final List<String> increments = new ArrayList<>();

        @Override
        public void increment(long userId, int delta, Instant at) {
            increments.add(userId + ":" + delta);
        }

        @Override
        public List<Entry> top(LeaderboardPeriod period, Instant at, int limit) {
            return List.of(new Entry(1L, 42));
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
