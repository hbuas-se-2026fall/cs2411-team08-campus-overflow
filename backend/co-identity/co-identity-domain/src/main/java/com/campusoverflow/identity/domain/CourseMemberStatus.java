package com.campusoverflow.identity.domain;

/**
 * 课程成员状态。移除采用软删除：REMOVED 成员立即失去课程内一切权限，
 * 保留记录以便审计；重新加入时复活原记录。
 */
public enum CourseMemberStatus {
    ACTIVE,
    REMOVED
}
