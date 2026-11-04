package com.campusoverflow.identity.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.Guard;
import com.campusoverflow.shared.security.Actor;
import java.time.Instant;
import java.util.regex.Pattern;

/** 课程：课程专属问答区的载体。同一课程代码在同一学期唯一。 */
public class Course {

    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9-]{2,20}$");

    private Long id;
    private final String code;
    private final String name;
    private final String term;
    private final long teacherId;
    private CourseStatus status;
    private final Instant createdAt;

    private Course(Long id, String code, String name, String term, long teacherId, CourseStatus status,
                   Instant createdAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.term = term;
        this.teacherId = teacherId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Course create(Actor actor, String code, String name, String term, Instant now) {
        if (actor == null || !actor.isTeacherOrAdmin()) {
            throw new ForbiddenException("ID-3002", "仅教师或管理员可以创建课程");
        }
        if (code == null || !CODE.matcher(code).matches()) {
            throw new BusinessRuleException("ID-1010", "课程代码应为 2–20 位字母、数字或连字符");
        }
        return new Course(null, code.toUpperCase(), Guard.requireText(name, 2, 50, "ID-1011", "课程名称"),
                Guard.requireText(term, 2, 20, "ID-1012", "学期"), actor.userId(), CourseStatus.OPEN, now);
    }

    public static Course reconstitute(Long id, String code, String name, String term, long teacherId,
                                      CourseStatus status, Instant createdAt) {
        return new Course(id, code, name, term, teacherId, status, createdAt);
    }

    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID 已分配");
        }
        this.id = id;
    }

    /** 课程管理权判定：本课程教师或平台管理员（防止课程 A 的管理者管理课程 B）。 */
    public void ensureManager(Actor actor) {
        if (actor == null || (!actor.isAdmin() && actor.userId() != teacherId)) {
            throw new ForbiddenException("ID-3003", "只有课程教师或管理员可以执行该操作");
        }
    }

    /** 归档课程：此后只读，不可加入，也不可再创建问题或悬赏。 */
    public void archive(Actor actor) {
        ensureManager(actor);
        if (status == CourseStatus.ARCHIVED) {
            throw new ConflictException("ID-2008", "该课程已归档");
        }
        this.status = CourseStatus.ARCHIVED;
    }

    /** 重开已归档的课程。 */
    public void reopen(Actor actor) {
        ensureManager(actor);
        if (status == CourseStatus.OPEN) {
            throw new ConflictException("ID-2009", "该课程未处于归档状态");
        }
        this.status = CourseStatus.OPEN;
    }

    /** 加入课程的前置校验：归档课程不可加入。 */
    public void ensureOpen() {
        if (status == CourseStatus.ARCHIVED) {
            throw new ConflictException("ID-2010", "课程已归档，无法加入");
        }
    }

    /** 移除课程成员：仅课程教师或平台管理员；课程教师本人（所有者）不可被移除。 */
    public void removeMember(Actor actor, CourseMember member, Instant now) {
        ensureManager(actor);
        if (member.role() == CourseRole.TEACHER) {
            throw new BusinessRuleException("ID-1014", "不能移除课程教师");
        }
        member.markRemoved(now);
    }

    public Long id() { return id; }
    public String code() { return code; }
    public String name() { return name; }
    public String term() { return term; }
    public long teacherId() { return teacherId; }
    public CourseStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
}
