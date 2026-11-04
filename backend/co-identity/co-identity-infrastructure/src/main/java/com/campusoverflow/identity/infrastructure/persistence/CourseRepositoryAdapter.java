package com.campusoverflow.identity.infrastructure.persistence;

import com.campusoverflow.identity.domain.Course;
import com.campusoverflow.identity.domain.CourseMember;
import com.campusoverflow.identity.domain.CourseMemberStatus;
import com.campusoverflow.identity.domain.CourseRepository;
import com.campusoverflow.identity.domain.CourseRole;
import com.campusoverflow.identity.domain.CourseStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class CourseRepositoryAdapter implements CourseRepository {

    private final CourseJpaRepository courses;
    private final CourseMemberJpaRepository members;

    public CourseRepositoryAdapter(CourseJpaRepository courses, CourseMemberJpaRepository members) {
        this.courses = courses;
        this.members = members;
    }

    @Override
    public Course save(Course course) {
        if (course.id() != null) {
            // 已有课程仅状态可变（归档/重开），其余字段不可变
            courses.findById(course.id()).ifPresent(e -> {
                e.setStatus(course.status().name());
                courses.save(e);
            });
            return course;
        }
        CourseJpaEntity saved = courses.save(new CourseJpaEntity(course.code(), course.name(), course.term(),
                course.teacherId(), course.status().name(), course.createdAt()));
        course.assignId(saved.getId());
        return course;
    }

    @Override
    public Optional<Course> findById(long id) {
        return courses.findById(id).map(CourseRepositoryAdapter::toDomain);
    }

    @Override
    public List<Course> findAll() {
        return courses.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(CourseRepositoryAdapter::toDomain).toList();
    }

    @Override
    public boolean existsByCodeAndTerm(String code, String term) {
        return courses.existsByCodeAndTerm(code, term);
    }

    @Override
    public Optional<CourseMember> findMember(long courseId, long userId) {
        return members.findByCourseIdAndUserId(courseId, userId).map(CourseRepositoryAdapter::toDomain)
                .filter(CourseMember::isActive);
    }

    @Override
    public Optional<CourseMember> findAnyMember(long courseId, long userId) {
        return members.findByCourseIdAndUserId(courseId, userId).map(CourseRepositoryAdapter::toDomain);
    }

    @Override
    public CourseMember saveMember(CourseMember member) {
        CourseMemberJpaEntity e = members.findByCourseIdAndUserId(member.courseId(), member.userId())
                .orElseGet(() -> new CourseMemberJpaEntity(member.courseId(), member.userId(), member.role().name(),
                        member.status().name(), member.joinedAt(), member.removedAt()));
        e.setRole(member.role().name());
        e.setStatus(member.status().name());
        e.setJoinedAt(member.joinedAt());
        e.setRemovedAt(member.removedAt());
        members.save(e);
        return member;
    }

    @Override
    public List<CourseMember> findMembers(long courseId) {
        return members.findByCourseId(courseId).stream().map(CourseRepositoryAdapter::toDomain)
                .filter(CourseMember::isActive).toList();
    }

    @Override
    public List<CourseMember> findMembershipsOfUser(long userId) {
        return members.findByUserId(userId).stream().map(CourseRepositoryAdapter::toDomain)
                .filter(CourseMember::isActive).toList();
    }

    private static Course toDomain(CourseJpaEntity e) {
        return Course.reconstitute(e.getId(), e.getCode(), e.getName(), e.getTerm(), e.getTeacherId(),
                CourseStatus.valueOf(e.getStatus()), e.getCreatedAt());
    }

    private static CourseMember toDomain(CourseMemberJpaEntity e) {
        return CourseMember.reconstitute(e.getCourseId(), e.getUserId(), CourseRole.valueOf(e.getRole()),
                CourseMemberStatus.valueOf(e.getStatus()), e.getJoinedAt(), e.getRemovedAt());
    }
}
