package com.campusoverflow.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CourseTest {

    private static final Instant NOW = Instant.parse("2026-09-01T08:00:00Z");
    private final Actor teacher = new Actor(5, "王老师", Role.TEACHER);
    private final Actor admin = new Actor(1, "admin", Role.ADMIN);
    private final Actor outsider = new Actor(9, "别人班的老师", Role.TEACHER);
    private final Actor student = new Actor(6, "alice", Role.STUDENT);

    private Course newCourse() {
        Course c = Course.create(teacher, "ds-101", "数据结构", "2026-秋", NOW);
        c.assignId(100);
        return c;
    }

    @Test
    void teacherCreatesCourseAndBecomesItsTeacher() {
        Course c = Course.create(new Actor(5, "王老师", Role.TEACHER), "ds-101", "数据结构", "2026-秋", NOW);
        assertThat(c.code()).isEqualTo("DS-101");
        assertThat(c.teacherId()).isEqualTo(5);
    }

    @Test
    void studentCannotCreateCourse() {
        assertThatThrownBy(() -> Course.create(new Actor(2, "alice", Role.STUDENT), "DS", "数据结构", "2026-秋", NOW))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void courseTeacherRoleCannotBeDowngraded() {
        CourseMember m = CourseMember.teacher(1, 5, NOW);
        assertThatThrownBy(() -> m.changeRole(CourseRole.STUDENT)).isInstanceOf(BusinessRuleException.class);
        CourseMember s = CourseMember.student(1, 6, NOW);
        s.changeRole(CourseRole.TA);
        assertThat(s.role().canManage()).isTrue();
    }

    @Test
    void onlyCourseTeacherOrAdminCanArchiveAndReopen() {
        Course c = newCourse();
        assertThatThrownBy(() -> c.archive(student)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> c.archive(outsider)).isInstanceOf(ForbiddenException.class);
        c.archive(admin);
        assertThat(c.status()).isEqualTo(CourseStatus.ARCHIVED);
        assertThatThrownBy(() -> c.archive(teacher)).isInstanceOf(ConflictException.class);
        c.reopen(teacher);
        assertThat(c.status()).isEqualTo(CourseStatus.OPEN);
        assertThatThrownBy(() -> c.reopen(teacher)).isInstanceOf(ConflictException.class);
    }

    @Test
    void archivedCourseCannotBeJoined() {
        Course c = newCourse();
        c.archive(teacher);
        assertThatThrownBy(c::ensureOpen).isInstanceOf(ConflictException.class);
    }

    @Test
    void teacherOrAdminRemovesMemberButNotTeacherItself() {
        Course c = newCourse();
        CourseMember owner = CourseMember.teacher(100, 5, NOW);
        CourseMember s = CourseMember.student(100, 6, NOW);
        // 学生/外班教师不能移除人
        assertThatThrownBy(() -> c.removeMember(student, s, NOW)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> c.removeMember(outsider, s, NOW)).isInstanceOf(ForbiddenException.class);
        // 课程教师（所有者）不可被移除
        assertThatThrownBy(() -> c.removeMember(admin, owner, NOW)).isInstanceOf(BusinessRuleException.class);
        // 教师移除学生 → 软删除
        c.removeMember(teacher, s, NOW.plusSeconds(60));
        assertThat(s.status()).isEqualTo(CourseMemberStatus.REMOVED);
        assertThat(s.removedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(s.isActive()).isFalse();
        assertThatThrownBy(() -> s.markRemoved(NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void removedMemberCanRejoinByReactivatingRecord() {
        CourseMember s = CourseMember.student(100, 6, NOW);
        s.markRemoved(NOW.plusSeconds(60));
        s.reactivate(NOW.plusSeconds(120));
        assertThat(s.status()).isEqualTo(CourseMemberStatus.ACTIVE);
        assertThat(s.removedAt()).isNull();
        assertThat(s.joinedAt()).isEqualTo(NOW.plusSeconds(120));
        assertThatThrownBy(() -> s.reactivate(NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void memberLeavesBySelfOnlyAndTeacherCannotLeave() {
        CourseMember s = CourseMember.student(100, 6, NOW);
        // 只有本人能退出
        assertThatThrownBy(() -> s.leaveBy(outsider, NOW)).isInstanceOf(ForbiddenException.class);
        CourseMember t = CourseMember.teacher(100, 5, NOW);
        // 课程教师是所有者，不能退出
        assertThatThrownBy(() -> t.leaveBy(teacher, NOW)).isInstanceOf(BusinessRuleException.class);
        s.leaveBy(student, NOW.plusSeconds(30));
        assertThat(s.status()).isEqualTo(CourseMemberStatus.REMOVED);
    }
}
