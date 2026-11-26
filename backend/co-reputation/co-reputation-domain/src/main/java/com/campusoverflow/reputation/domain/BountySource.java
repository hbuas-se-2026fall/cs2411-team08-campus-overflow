package com.campusoverflow.reputation.domain;

/**
 * 悬赏的资金来源。授权规则以枚举 + Lambda 表达（OCP）：新增来源只需追加常量与规则，
 * 调用方无需 if/switch 类型分支。courseId 为 null 表示问题未关联课程。
 */
public enum BountySource {
    /** 学生/教师个人账户积分：提问者本人或课程管理者可发起。 */
    PERSONAL((actorId, authorId, manages, courseId) -> actorId == authorId || manages),
    /** 课程预算积分：仅课程管理者、且问题必须关联课程。 */
    COURSE_BUDGET((actorId, authorId, manages, courseId) -> manages && courseId != null);

    @FunctionalInterface
    interface OpeningRule {
        boolean allows(long actorId, long authorId, boolean manages, Long courseId);
    }

    private final OpeningRule rule;

    BountySource(OpeningRule rule) {
        this.rule = rule;
    }

    public boolean allows(long actorId, long authorId, boolean manages, Long courseId) {
        return rule.allows(actorId, authorId, manages, courseId);
    }
}
