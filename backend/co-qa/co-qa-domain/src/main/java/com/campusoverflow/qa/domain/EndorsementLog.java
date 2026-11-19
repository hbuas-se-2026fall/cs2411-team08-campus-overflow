package com.campusoverflow.qa.domain;

import com.campusoverflow.shared.domain.Guard;
import java.time.Instant;
import java.util.Objects;

/**
 * 认证历史（只追加账本）：一次授予或一次撤销就是一条记录，不修改、不删除。
 * qa_answer.endorsed_by 只表示"当前有效认证"，撤销后由应用层置空。
 */
public class EndorsementLog {

    public enum Action { GRANT, REVOKE }

    private Long id;
    private final long answerId;
    private final long endorserId;
    private final long answerVersion;
    private final Action action;
    private final String reason;
    private final Instant createdAt;

    private EndorsementLog(Long id, long answerId, long endorserId, long answerVersion, Action action,
                           String reason, Instant createdAt) {
        this.id = id;
        this.answerId = answerId;
        this.endorserId = endorserId;
        this.answerVersion = answerVersion;
        this.action = action;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public static EndorsementLog grant(long answerId, long endorserId, long answerVersion, Instant now) {
        return new EndorsementLog(null, answerId, endorserId, answerVersion, Action.GRANT, null,
                Objects.requireNonNull(now));
    }

    public static EndorsementLog revoke(long answerId, long endorserId, long answerVersion, String reason,
                                        Instant now) {
        return new EndorsementLog(null, answerId, endorserId, answerVersion, Action.REVOKE,
                Guard.requireText(reason, 1, 500, "QA-1032", "撤销原因"), Objects.requireNonNull(now));
    }

    public static EndorsementLog reconstitute(Long id, long answerId, long endorserId, long answerVersion,
                                               Action action, String reason, Instant createdAt) {
        return new EndorsementLog(id, answerId, endorserId, answerVersion, action, reason, createdAt);
    }

    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID 已分配");
        }
        this.id = id;
    }

    public Long id() { return id; }
    public long answerId() { return answerId; }
    public long endorserId() { return endorserId; }
    public long answerVersion() { return answerVersion; }
    public Action action() { return action; }
    public String reason() { return reason; }
    public Instant createdAt() { return createdAt; }
}
