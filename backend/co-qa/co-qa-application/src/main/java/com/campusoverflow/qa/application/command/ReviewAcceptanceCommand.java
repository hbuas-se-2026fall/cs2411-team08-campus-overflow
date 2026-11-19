package com.campusoverflow.qa.application.command;

/** 教师复核采纳：理由必填，expectedVersion 用于乐观锁，防止并发下撤销了错误的采纳。 */
public record ReviewAcceptanceCommand(long expectedVersion, String reason) {
}
