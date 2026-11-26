package com.campusoverflow.reputation.application;

import com.campusoverflow.qa.api.event.AnswerUnacceptedEvent;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 悬赏的事件驱动后半段：问题删除退款、获奖后判错留痕。终态抢占保证重放不重复。 */
@Component
public class BountyEventHandlers {

    private final BountyService bounties;

    public BountyEventHandlers(BountyService bounties) {
        this.bounties = bounties;
    }

    @EventListener
    @Transactional
    public void on(ContentDeletedEvent e) {
        if ("QUESTION".equals(e.targetType())) {
            bounties.refundOnQuestionDeleted(e.questionId(), e.eventId());
        }
    }

    @EventListener
    @Transactional
    public void on(AnswerUnacceptedEvent e) {
        bounties.flagDisputed(e.questionId(), e.answerAuthorId(), e.eventId());
    }
}
