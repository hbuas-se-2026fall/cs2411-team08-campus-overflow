package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyClosedEvent;
import com.campusoverflow.reputation.api.event.BountyOpenedEvent;
import com.campusoverflow.reputation.domain.Bounty;
import com.campusoverflow.reputation.domain.BountyRepository;
import com.campusoverflow.reputation.domain.BountySource;
import com.campusoverflow.reputation.domain.BountyStatus;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.command.CommandIdempotencyStore;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用例：悬赏的发起、结算、到期/删除退款、争议留痕。
 * 冻结与悬赏状态在同一事务中变更；终态由条件更新抢占，资金来源由 BountyFunding 策略分派。
 */
@Service
public class BountyService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final BountyRepository bounties;
    private final ReputationLedgerService ledgerService;
    private final QaQueryApi qa;
    private final IdentityApi identity;
    private final IntegrationEventPublisher events;
    private final AuditTrail audit;
    private final Clock clock;
    private final BountyFundingRegistry funding;
    private final CommandIdempotencyStore idempotency;

    @Autowired
    public BountyService(BountyRepository bounties, ReputationLedgerService ledgerService, QaQueryApi qa,
                         IdentityApi identity, IntegrationEventPublisher events, AuditTrail audit, Clock clock,
                         BountyFundingRegistry funding, CommandIdempotencyStore idempotency) {
        this.bounties = bounties;
        this.ledgerService = ledgerService;
        this.qa = qa;
        this.identity = identity;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
        this.funding = funding;
        this.idempotency = idempotency;
    }

    /** 兼容既有单元测试：只启用个人账户来源、不启用命令幂等。 */
    public BountyService(BountyRepository bounties, ReputationAccountRepository accounts,
                         ReputationLedgerService ledgerService, QaQueryApi qa, IdentityApi identity,
                         IntegrationEventPublisher events, AuditTrail audit, Clock clock) {
        this(bounties, ledgerService, qa, identity, events, audit, clock,
                BountyFundingRegistry.personalOnly(accounts),
                NoOpCommandIdempotencyStore.INSTANCE);
    }

    /** B05 既有签名：个人账户、无命令幂等键。 */
    @Transactional
    public long open(Actor actor, long questionId, int points, int days) {
        return open(actor, questionId, points, days, null, BountySource.PERSONAL, null);
    }

    /**
     * B06 签名：commandId 由客户端提供（同一请求重放返回首次结果）；
     * 未提供时仍由 uk_bounty_open_question 唯一索引防并发双开，两道闸互相独立。
     */
    @Transactional
    public long open(Actor actor, long questionId, int points, int days, String commandId,
                     BountySource source, Long courseId) {
        String key = commandId == null || commandId.isBlank() ? null : "bounty:open:" + commandId;
        if (key != null) {
            CommandIdempotencyStore.BeginOutcome outcome = idempotency.begin(key, IDEMPOTENCY_TTL, clock.instant());
            if (outcome == CommandIdempotencyStore.BeginOutcome.ALREADY_SUCCEEDED) {
                return idempotency.resultOf(key).map(Long::parseLong)
                        .orElseThrow(() -> new ConflictException("REP-2007", "悬赏已提交，但结果摘要缺失"));
            }
            if (outcome == CommandIdempotencyStore.BeginOutcome.IN_PROGRESS) {
                throw new ConflictException("REP-2007", "悬赏正在处理中，请稍后重试");
            }
        }
        QuestionRef question = qa.findQuestion(questionId)
                .orElseThrow(() -> new NotFoundException("REP-4001", "问题不存在"));
        if (source == BountySource.COURSE_BUDGET) {
            long requiredCourseId = courseId == null ? requireCourseId(question) : courseId;
            identity.findCourse(requiredCourseId)
                    .orElseThrow(() -> new NotFoundException("REP-5005", "课程不存在"));
        }
        boolean allowed = authorize(actor, question, source);
        long sourceId = source == BountySource.PERSONAL ? actor.userId() : requireCourseId(question, courseId);
        boolean questionOpen = !question.deleted() && question.acceptedAnswerId() == null;
        Bounty bounty = Bounty.open(questionId, questionOpen, actor, allowed, points, days, clock.instant(),
                source, sourceId, key);
        Bounty saved = bounties.save(bounty);
        BountyOperationContext context = operationContext(saved, key == null ? null : key);
        funding.fundingFor(source).freeze(sourceId, points, context);
        events.publish(new BountyOpenedEvent(EventIds.next(), clock.instant(), saved.id(), questionId, actor.userId(),
                points, saved.expiresAt()));
        audit.record("BOUNTY_OPENED", actor.userId(), "QUESTION", String.valueOf(questionId),
                "points=" + points + ",source=" + source);
        if (key != null) {
            idempotency.complete(key, String.valueOf(saved.id()), clock.instant());
        }
        return saved.id();
    }

    /** 由 AnswerAccepted 事件触发（在处理器事务内）。幂等：终态已被抢占则直接返回。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void settleOnAccept(long questionId, long answerId, long winnerId, String sourceEventId) {
        Optional<Bounty> open = bounties.findOpenByQuestion(questionId);
        if (open.isEmpty()) {
            return;
        }
        Bounty bounty = open.get();
        Instant now = clock.instant();
        BountyFunding strategy = funding.fundingFor(bounty.source());
        BountyOperationContext context = operationContext(bounty, null);
        if (winnerId == bounty.sponsorId()) {
            if (!bounties.tryExpire(bounty.id(), now)) {
                return;
            }
            bounty.close(now);
            strategy.refund(bounty.sourceId(), bounty.points(), context);
            publishClosed(bounty, now);
            return;
        }
        bounty.award(answerId, winnerId, now);
        if (!bounties.tryAward(bounty.id(), answerId, winnerId, now)) {
            return;
        }
        strategy.spend(bounty.sourceId(), bounty.points(), context);
        ledgerService.record(winnerId, sourceEventId, ReputationReason.BOUNTY_AWARDED, bounty.points(),
                "QUESTION", questionId);
        events.publish(new BountyAwardedEvent(EventIds.next(), now, bounty.id(), questionId, bounty.sponsorId(),
                winnerId, bounty.points()));
        audit.record("BOUNTY_AWARDED", winnerId, "QUESTION", String.valueOf(questionId),
                "bountyId=" + bounty.id());
    }

    /** 到期未采纳：关闭并退回冻结积分（个人退本人、预算退课程）。由定时任务调用。 */
    @Transactional
    public int expireDue() {
        Instant now = clock.instant();
        List<Bounty> due = bounties.findDue(now, 100);
        int closed = 0;
        for (Bounty bounty : due) {
            if (!bounties.tryExpire(bounty.id(), now)) {
                continue;
            }
            bounty.close(now);
            refundAndClose(bounty, now);
            closed++;
        }
        return closed;
    }

    /** 问题被软删除：其开放悬赏立即关闭退款（事件驱动，与到期共用终态闸）。 */
    @Transactional
    public void refundOnQuestionDeleted(long questionId, String eventId) {
        bounties.findOpenByQuestion(questionId).ifPresent(bounty -> {
            Instant now = clock.instant();
            if (!bounties.tryExpire(bounty.id(), now)) {
                return;
            }
            bounty.close(now);
            refundAndClose(bounty, now);
            audit.record("BOUNTY_REFUND_ON_DELETE", bounty.sponsorId(), "QUESTION", String.valueOf(questionId),
                    "bountyId=" + bounty.id() + ",event=" + eventId);
        });
    }

    /** 获奖后判错：只标记待复核，不回退结算、不强扣积分（D8）。 */
    @Transactional
    public void flagDisputed(long questionId, long answerAuthorId, String eventId) {
        bounties.findLatestByQuestion(questionId).ifPresent(bounty -> {
            if (bounty.status() != BountyStatus.AWARDED || bounty.winnerId() == null
                    || bounty.winnerId() != answerAuthorId || bounty.disputed()) {
                return;
            }
            bounty.markDisputed();
            bounties.markDisputed(bounty.id());
            audit.record("BOUNTY_AWARD_DISPUTED", answerAuthorId, "QUESTION", String.valueOf(questionId),
                    "bountyId=" + bounty.id() + ",event=" + eventId);
        });
    }

    @Transactional(readOnly = true)
    public Optional<BountyView> latest(long questionId) {
        return bounties.findLatestByQuestion(questionId).map(bounty -> {
            String sponsorName = identity.findUser(bounty.sponsorId())
                    .map(UserSummary::displayName).orElse("未知用户");
            Long courseId = bounty.source() == BountySource.COURSE_BUDGET ? bounty.sourceId() : null;
            return new BountyView(bounty.id(), bounty.questionId(), bounty.sponsorId(), sponsorName, bounty.points(),
                    bounty.status().name(), bounty.createdAt(), bounty.expiresAt(), bounty.winnerId(),
                    bounty.answerId(), bounty.source().name(), courseId, bounty.disputed());
        });
    }

    private boolean authorize(Actor actor, QuestionRef question, BountySource source) {
        boolean manages = identity.canManageCourse(actor.userId(), question.courseId());
        return source.allows(actor.userId(), question.authorId(), manages, question.courseId());
    }

    private static long requireCourseId(QuestionRef question) {
        if (question.courseId() == null) {
            throw new NotFoundException("REP-5005", "问题未关联课程，不能使用课程预算");
        }
        return question.courseId();
    }

    private static long requireCourseId(QuestionRef question, Long courseId) {
        long fromQuestion = requireCourseId(question);
        if (courseId != null && courseId != fromQuestion) {
            throw new ConflictException("REP-2008", "悬赏课程与问题所属课程不一致");
        }
        return fromQuestion;
    }

    private BountyOperationContext operationContext(Bounty bounty, String commandKey) {
        String eventId = commandKey == null ? "bounty:" + bounty.id() : commandKey;
        return new BountyOperationContext(bounty.id(), bounty.questionId(), bounty.sponsorId(), eventId,
                clock.instant());
    }

    private void refundAndClose(Bounty bounty, Instant now) {
        BountyOperationContext context = operationContext(bounty, null);
        funding.fundingFor(bounty.source()).refund(bounty.sourceId(), bounty.points(), context);
        publishClosed(bounty, now);
    }

    private void publishClosed(Bounty bounty, Instant now) {
        events.publish(new BountyClosedEvent(EventIds.next(), now, bounty.id(), bounty.questionId(),
                bounty.sponsorId(), bounty.points()));
        audit.record("BOUNTY_CLOSED", bounty.sponsorId(), "QUESTION", String.valueOf(bounty.questionId()),
                "refunded=" + bounty.points());
    }
}
