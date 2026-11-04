package com.campusoverflow.identity.api;

/**
 * 课程摘要（发布语言）。status 取值 "OPEN" / "ARCHIVED"（FF-5 限制 api 包不得依赖领域类型，
 * 故用字符串而非枚举），供其他上下文实现"归档课程只读"（禁止创建问题/悬赏）。
 */
public record CourseSummary(long id, String code, String name, String term, String status, long teacherId) {
}
