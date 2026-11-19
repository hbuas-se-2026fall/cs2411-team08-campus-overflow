package com.campusoverflow.qa.application.command;

import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.AnswerRepository;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.QuestionRepository;
import com.campusoverflow.qa.domain.TargetType;
import com.campusoverflow.qa.domain.Vote;
import com.campusoverflow.qa.domain.VoteRepository;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用例：投票 / 改投 / 撤销（value ∈ {-1, 0, 1}，0 表示撤销）。
 * 发布的事件同时携带旧值与新值，下游据此计算声誉增量。
 */
@Service
public class VoteService {

    private final VoteRepository votes;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public VoteService(VoteRepository votes, QuestionRepository questions, AnswerRepository answers,
                       IntegrationEventPublisher events, Clock clock) {
        this.votes = votes;
        this.questions = questions;
        this.answers = answers;
        this.events = events;
        this.clock = clock;
    }

    public record VoteResult(int myVote, int scoreDelta) {
    }

    private record Target(long authorId, long questionId) {
    }

    @Transactional
    public VoteResult cast(Actor actor, TargetType type, long targetId, int value) {
        Target target = resolve(type, targetId);
        long voterId = actor.userId();
        Instant now = clock.instant();
        Optional<Vote> existing = votes.find(voterId, type, targetId);

        if (existing.isEmpty()) {
            Vote vote = Vote.cast(voterId, target.authorId(), type, targetId, value, now);
            votes.save(vote);
            applyScoreDelta(type, targetId, value);
            publishVoteEvent(type, targetId, target, voterId, 0, value, now);
            return new VoteResult(value, value);
        }
        Vote vote = existing.get();
        if (value == vote.value()) {
            return new VoteResult(vote.value(), 0); // 同值重复投票：幂等，不重发事件
        }
        if (value == 0) {
            votes.delete(vote);
            applyScoreDelta(type, targetId, -vote.value());
            publishVoteEvent(type, targetId, target, voterId, vote.value(), 0, now);
            return new VoteResult(0, -vote.value());
        }
        int old = vote.change(value, now);
        votes.save(vote);
        applyScoreDelta(type, targetId, value - old);
        publishVoteEvent(type, targetId, target, voterId, old, value, now);
        return new VoteResult(value, value - old);
    }

    private Target resolve(TargetType type, long targetId) {
        return switch (type) {
            case QUESTION -> {
                Question q = questions.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("QA-2004", "问题不存在"));
                if (q.isDeleted()) {
                    throw new BusinessRuleException("QA-1003", "问题已删除");
                }
                yield new Target(q.authorId(), q.id());
            }
            case ANSWER -> {
                Answer a = answers.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("QA-2006", "回答不存在"));
                if (a.isDeleted()) {
                    throw new BusinessRuleException("QA-1016", "回答已删除");
                }
                yield new Target(a.authorId(), a.questionId());
            }
        };
    }

    private void applyScoreDelta(TargetType type, long targetId, int delta) {
        if (delta == 0) {
            return;
        }
        if (type == TargetType.QUESTION) {
            questions.adjustScore(targetId, delta);
        } else {
            answers.adjustScore(targetId, delta);
        }
    }

    private void publishVoteEvent(TargetType type, long targetId, Target target, long voterId, int oldValue,
                                  int newValue, Instant now) {
        events.publish(new VoteCastEvent(EventIds.next(), now, type.name(), targetId, target.authorId(), voterId,
                target.questionId(), oldValue, newValue));
    }
}
