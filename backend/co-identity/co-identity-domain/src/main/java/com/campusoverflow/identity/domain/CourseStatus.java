package com.campusoverflow.identity.domain;

/** 课程生命周期状态。归档后课程只读：不可加入，也不可再创建问题或悬赏。 */
public enum CourseStatus {
    OPEN,
    ARCHIVED
}
