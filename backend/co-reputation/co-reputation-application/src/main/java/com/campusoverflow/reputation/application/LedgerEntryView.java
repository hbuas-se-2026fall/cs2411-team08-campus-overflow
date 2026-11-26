package com.campusoverflow.reputation.application;

import java.time.Instant;

/** 账本流水视图。reversesEventId 非空表示这条是冲正流水，指向被冲正原流水的 eventId。 */
public record LedgerEntryView(long id, String reason, String reasonLabel, int delta, String refType, long refId,
                              Instant createdAt, String reversesEventId) {
}
