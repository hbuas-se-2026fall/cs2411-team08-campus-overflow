package com.campusoverflow.reputation.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.campusoverflow.qa.api.event.AnswerUnacceptedEvent;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** B06 事件处理器：问题删除触发退款，回答删除忽略，撤销采纳触发争议留痕。 */
class BountyEventHandlersTest {

    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");

    private final BountyService bounties = mock(BountyService.class);
    private final BountyEventHandlers handlers = new BountyEventHandlers(bounties);

    @Test
    void questionDeletedEventTriggersRefund() {
        handlers.on(new ContentDeletedEvent("d1", NOW, "QUESTION", 10, 10));

        verify(bounties).refundOnQuestionDeleted(10, "d1");
    }

    @Test
    void answerDeletedEventIsIgnored() {
        handlers.on(new ContentDeletedEvent("d2", NOW, "ANSWER", 50, 10));

        verify(bounties, never()).refundOnQuestionDeleted(10, "d2");
    }

    @Test
    void answerUnacceptedEventFlagsDispute() {
        handlers.on(new AnswerUnacceptedEvent("u1", NOW, 50, 10, 3, 1, 9, "问题10"));

        verify(bounties).flagDisputed(10, 3, "u1");
    }
}
