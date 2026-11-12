package com.campusoverflow.identity.domain;

import java.util.List;
import java.util.Optional;

public interface CourseRepository {
    Course save(Course course);

    Optional<Course> findById(long id);

    List<Course> findAll();

    boolean existsByCodeAndTerm(String code, String term);

    Optional<CourseMember> findMember(long courseId, long userId);

    /** 不过滤状态：含已移除（REMOVED）的成员记录，用于重新加入时复活。 */
    Optional<CourseMember> findAnyMember(long courseId, long userId);

    CourseMember saveMember(CourseMember member);

    /** 仅有效（ACTIVE）成员。 */
    List<CourseMember> findMembers(long courseId);

    /** 仅有效（ACTIVE）的课程关系。 */
    List<CourseMember> findMembershipsOfUser(long userId);
}
