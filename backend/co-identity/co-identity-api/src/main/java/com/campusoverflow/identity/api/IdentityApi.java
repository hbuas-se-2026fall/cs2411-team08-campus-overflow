package com.campusoverflow.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Identity 上下文的开放主机服务（OHS）。其他上下文只能通过本接口查询用户与课程信息，
 * 不得直接访问 Identity 的领域模型或数据表（由 ArchUnit 规则 FF-2 守护）。
 */
public interface IdentityApi {

    Optional<UserSummary> findUser(long userId);

    /** 批量查询，避免 N+1。返回 Map 的 key 为用户 ID，不存在的 ID 不会出现在结果中。 */
    Map<Long, UserSummary> findUsers(Collection<Long> userIds);

    Optional<UserSummary> findByDisplayName(String displayName);

    Optional<CourseSummary> findCourse(long courseId);

    /**
     * 是否有权管理课程内的内容（采纳、教师认证、发布悬赏等）：
     * 管理员；或该课程的教师/助教。courseId 为 null（未关联课程的问题）时，全局教师角色亦可。
     */
    boolean canManageCourse(long userId, Long courseId);

    /**
     * 是否为该课程的<b>有效</b>成员（"成员可参与本课程问答"的判定入口）。
     * 已被移除（REMOVED）的成员返回 false，立即失去课程内的读取与参与权限。
     */
    boolean isCourseMember(long userId, long courseId);
}
