package com.campusoverflow.identity.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import java.time.Instant;
import java.util.Objects;

/** 课程成员关系：授权判定（谁能在该课程中采纳、认证、悬赏）的依据。移除为软删除。 */
public class CourseMember {

    private final long courseId;
    private final long userId;
    private CourseRole role;
    private CourseMemberStatus status;
    private Instant joinedAt;
    private Instant removedAt;

    private CourseMember(long courseId, long userId, CourseRole role, CourseMemberStatus status,
                         Instant joinedAt, Instant removedAt) {
        this.courseId = courseId;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.joinedAt = joinedAt;
        this.removedAt = removedAt;
    }

    public static CourseMember student(long courseId, long userId, Instant now) {
        return new CourseMember(courseId, userId, CourseRole.STUDENT, CourseMemberStatus.ACTIVE, now, null);
    }

    public static CourseMember teacher(long courseId, long userId, Instant now) {
        return new CourseMember(courseId, userId, CourseRole.TEACHER, CourseMemberStatus.ACTIVE, now, null);
    }

    public static CourseMember reconstitute(long courseId, long userId, CourseRole role,
                                            CourseMemberStatus status, Instant joinedAt, Instant removedAt) {
        return new CourseMember(courseId, userId, role, status, joinedAt, removedAt);
    }

    /** 调整课程内角色（如任命助教）。课程教师本人的角色不可被降级。 */
    public void changeRole(CourseRole newRole) {
        if (role == CourseRole.TEACHER && newRole != CourseRole.TEACHER) {
            throw new BusinessRuleException("ID-1013", "不能修改课程教师的角色");
        }
        this.role = Objects.requireNonNull(newRole);
    }

    /** 被课程教师/平台管理员移除（软删除）。重复移除视为状态冲突。 */
    public void markRemoved(Instant now) {
        if (status == CourseMemberStatus.REMOVED) {
            throw new ConflictException("ID-2011", "该成员已不在课程中");
        }
        this.status = CourseMemberStatus.REMOVED;
        this.removedAt = now;
    }

    /** 本人退出课程。课程教师是所有者，不能退出（须先转让或归档）。 */
    public void leaveBy(Actor actor, Instant now) {
        if (actor == null || !actor.is(userId)) {
            throw new ForbiddenException("ID-3002", "只能退出本人加入的课程");
        }
        if (role == CourseRole.TEACHER) {
            throw new BusinessRuleException("ID-1015", "课程教师不能退出自己的课程");
        }
        markRemoved(now);
    }

    /** 被移除成员重新加入：复活原记录并刷新加入时间。 */
    public void reactivate(Instant now) {
        if (status == CourseMemberStatus.ACTIVE) {
            throw new ConflictException("ID-2005", "你已加入该课程");
        }
        this.status = CourseMemberStatus.ACTIVE;
        this.removedAt = null;
        this.joinedAt = now;
    }

    public boolean isActive() {
        return status == CourseMemberStatus.ACTIVE;
    }

    public long courseId() { return courseId; }
    public long userId() { return userId; }
    public CourseRole role() { return role; }
    public CourseMemberStatus status() { return status; }
    public Instant joinedAt() { return joinedAt; }
    public Instant removedAt() { return removedAt; }
}
