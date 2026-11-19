package com.campusoverflow.qa.application.command;

import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.AnswerRepository;
import com.campusoverflow.qa.domain.Comment;
import com.campusoverflow.qa.domain.CommentRepository;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.QuestionRepository;
import com.campusoverflow.qa.domain.TargetType;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：评论与二级回复。评论中的 @昵称 由 Discovery 解析并发送提醒。 */
@Service
public class CommentService {

    private final CommentRepository comments;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public CommentService(CommentRepository comments, QuestionRepository questions, AnswerRepository answers,
                          IntegrationEventPublisher events, Clock clock) {
        this.comments = comments;
        this.questions = questions;
        this.answers = answers;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public long post(Actor actor, PostCommentCommand cmd) {
        long questionId = resolveQuestionId(cmd);
        Comment parent = cmd.parentId() == null ? null
                : comments.findById(cmd.parentId())
                        .orElseThrow(() -> new NotFoundException("QA-2007", "回复的评论不存在"));

        Comment comment = comments.save(Comment.post(cmd.targetType(), cmd.targetId(), questionId,
                actor.userId(), parent, cmd.body(), clock.instant()));
        events.publish(new CommentPostedEvent(EventIds.next(), clock.instant(), comment.id(), questionId,
                actor.userId(), comment.body()));
        return comment.id();
    }

    /** 回复关系不能跨目标造伪：questionId 始终取自目标内容本身，而非客户端传入。 */
    private long resolveQuestionId(PostCommentCommand cmd) {
        return switch (cmd.targetType()) {
            case QUESTION -> {
                Question question = questions.findById(cmd.targetId())
                        .orElseThrow(() -> new NotFoundException("QA-2004", "问题不存在"));
                if (question.isDeleted()) {
                    throw new BusinessRuleException("QA-1003", "问题已删除");
                }
                yield question.id();
            }
            case ANSWER -> {
                Answer answer = answers.findById(cmd.targetId())
                        .orElseThrow(() -> new NotFoundException("QA-2006", "回答不存在"));
                if (answer.isDeleted()) {
                    throw new BusinessRuleException("QA-1016", "回答已删除");
                }
                yield answer.questionId();
            }
        };
    }
}
