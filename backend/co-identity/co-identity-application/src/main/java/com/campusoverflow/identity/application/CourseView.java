package com.campusoverflow.identity.application;

import com.campusoverflow.identity.domain.CourseStatus;

public record CourseView(long id, String code, String name, String term, CourseStatus status,
                         long teacherId, String teacherName, String myRole) {
}
