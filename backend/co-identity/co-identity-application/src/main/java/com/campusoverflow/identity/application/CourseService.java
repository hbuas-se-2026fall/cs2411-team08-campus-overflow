package com.campusoverflow.identity.application;

import com.campusoverflow.identity.domain.Course;
import com.campusoverflow.identity.domain.CourseMember;
import com.campusoverflow.identity.domain.CourseRepository;
import com.campusoverflow.identity.domain.CourseRole;
import com.campusoverflow.identity.domain.CourseStatus;
import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserRepository;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.PermissionCache;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：课程与课程成员管理。成员关系变更后立即失效受影响用户的权限缓存（B01）。 */
@Service
public class CourseService {

    private final CourseRepository courses;
    private final UserRepository users;
    private final Clock clock;
    private final PermissionCache permissionCache;

    public CourseService(CourseRepository courses, UserRepository users, Clock clock,
                         PermissionCache permissionCache) {
        this.courses = courses;
        this.users = users;
        this.clock = clock;
        this.permissionCache = permissionCache;
    }

    @Transactional
    public long create(Actor actor, CreateCourseCommand cmd) {
        Course course = Course.create(actor, cmd.code(), cmd.name(), cmd.term(), clock.instant());
        if (courses.existsByCodeAndTerm(course.code(), course.term())) {
            throw new ConflictException("ID-2004", "该学期已存在同代码课程");
        }
        Course saved = courses.save(course);
        courses.saveMember(CourseMember.teacher(saved.id(), actor.userId(), clock.instant()));
        return saved.id();
    }

    @Transactional
    public void join(Actor actor, long courseId) {
        Course course = loadCourse(courseId);
        course.ensureOpen(); // 归档课程不可加入
        // 被移除过的成员可重新加入：复活原记录（软删除保留痕迹）
        CourseMember returning = courses.findAnyMember(courseId, actor.userId()).orElse(null);
        if (returning == null) {
            courses.saveMember(CourseMember.student(courseId, actor.userId(), clock.instant()));
        } else {
            returning.reactivate(clock.instant());
            courses.saveMember(returning);
        }
        permissionCache.evictAll(String.valueOf(actor.userId()));
    }

    /** 课程教师或管理员调整成员角色（例如任命助教）。 */
    @Transactional
    public void assignRole(Actor actor, long courseId, long userId, CourseRole role) {
        Course course = loadCourse(courseId);
        course.ensureManager(actor); // 课程 A 的管理者不能管理课程 B
        CourseMember member = courses.findMember(courseId, userId)
                .orElseThrow(() -> new NotFoundException("ID-4003", "该用户不是课程成员"));
        member.changeRole(role);
        courses.saveMember(member);
        permissionCache.evictAll(String.valueOf(userId));
    }

    /** 移除课程成员（软删除）：教师/管理员操作，教师本人不可被移除。 */
    @Transactional
    public void removeMember(Actor actor, long courseId, long userId) {
        Course course = loadCourse(courseId);
        CourseMember member = courses.findAnyMember(courseId, userId)
                .orElseThrow(() -> new NotFoundException("ID-4003", "该用户不是课程成员"));
        course.removeMember(actor, member, clock.instant());
        courses.saveMember(member);
        permissionCache.evictAll(String.valueOf(userId));
    }

    /** 本人退出课程。 */
    @Transactional
    public void leave(Actor actor, long courseId) {
        loadCourse(courseId);
        CourseMember member = courses.findMember(courseId, actor.userId())
                .orElseThrow(() -> new NotFoundException("ID-4003", "你不在此课程中"));
        member.leaveBy(actor, clock.instant());
        courses.saveMember(member);
        permissionCache.evictAll(String.valueOf(actor.userId()));
    }

    /** 归档 / 重开课程。 */
    @Transactional
    public void changeStatus(Actor actor, long courseId, CourseStatus target) {
        Course course = loadCourse(courseId);
        if (target == CourseStatus.ARCHIVED) {
            course.archive(actor);
        } else {
            course.reopen(actor);
        }
        courses.save(course);
    }

    @Transactional(readOnly = true)
    public List<CourseView> listAll(Actor actorOrNull) {
        List<Course> all = courses.findAll();
        Map<Long, CourseRole> mine = actorOrNull == null ? Map.of()
                : membershipsOf(actorOrNull.userId());
        return toViews(all, mine);
    }

    @Transactional(readOnly = true)
    public List<CourseView> myCourses(long userId) {
        Map<Long, CourseRole> mine = membershipsOf(userId);
        List<Course> list = courses.findAll().stream().filter(c -> mine.containsKey(c.id())).toList();
        return toViews(list, mine);
    }

    /** 课程成员名单：仅课程成员（任意角色）或平台管理员可查看。 */
    @Transactional(readOnly = true)
    public List<CourseMemberView> members(long courseId, Actor actor) {
        loadCourse(courseId);
        boolean isMember = actor != null
                && courses.findMember(courseId, actor.userId()).isPresent();
        if (!isMember && (actor == null || !actor.isAdmin())) {
            throw new ForbiddenException("ID-3004", "仅课程成员或管理员可以查看成员名单");
        }
        List<CourseMember> list = courses.findMembers(courseId);
        Map<Long, User> byId = users.findAllById(list.stream().map(CourseMember::userId).toList()).stream()
                .collect(Collectors.toMap(User::id, Function.identity()));
        return list.stream()
                .map(m -> new CourseMemberView(m.userId(),
                        byId.containsKey(m.userId()) ? byId.get(m.userId()).displayName() : "未知用户", m.role()))
                .toList();
    }

    private Map<Long, CourseRole> membershipsOf(long userId) {
        return courses.findMembershipsOfUser(userId).stream()
                .collect(Collectors.toMap(CourseMember::courseId, CourseMember::role));
    }

    private List<CourseView> toViews(List<Course> list, Map<Long, CourseRole> mine) {
        Map<Long, String> teacherNames = users.findAllById(list.stream().map(Course::teacherId).distinct().toList())
                .stream().collect(Collectors.toMap(User::id, User::displayName));
        return list.stream()
                .map(c -> new CourseView(c.id(), c.code(), c.name(), c.term(), c.status(), c.teacherId(),
                        teacherNames.getOrDefault(c.teacherId(), "—"),
                        mine.containsKey(c.id()) ? mine.get(c.id()).name() : null))
                .toList();
    }

    private Course loadCourse(long courseId) {
        return courses.findById(courseId).orElseThrow(() -> new NotFoundException("ID-4002", "课程不存在"));
    }
}
