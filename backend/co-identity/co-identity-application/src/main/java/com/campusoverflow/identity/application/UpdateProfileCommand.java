package com.campusoverflow.identity.application;

/** 本人资料修改命令。仅含允许修改的字段，不含角色/认证状态。 */
public record UpdateProfileCommand(String displayName, String college, String className) {
}
