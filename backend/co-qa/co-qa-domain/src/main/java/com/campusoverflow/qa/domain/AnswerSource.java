package com.campusoverflow.qa.domain;

/** 回答来源。由服务端写入，客户端不可把系统来源改成 HUMAN。 */
public enum AnswerSource {
    HUMAN,
    AI,
    AI_ASSISTED
}
