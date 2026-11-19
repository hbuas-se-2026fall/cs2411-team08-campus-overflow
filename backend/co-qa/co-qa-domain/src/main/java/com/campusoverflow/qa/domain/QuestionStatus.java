package com.campusoverflow.qa.domain;

/** 问题状态。软删除仍以 deletedAt 为准，这里的状态只表达治理与解答进度。 */
public enum QuestionStatus {
    OPEN,
    RESOLVED,
    CLOSED,
    DELETED
}
