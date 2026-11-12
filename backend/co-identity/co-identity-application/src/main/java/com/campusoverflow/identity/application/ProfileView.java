package com.campusoverflow.identity.application;

import com.campusoverflow.shared.security.Role;
import java.time.Instant;

/**
 * 个人主页视图。公开字段任何登录用户可见；
 * {@code self} 仅在查看本人时非 null（携带 username/email/className 等私有信息）。
 */
public record ProfileView(long id, String displayName, Role role, boolean verified,
                          String college, Instant createdAt, SelfInfo self) {

    /** 仅本人可见的私有信息块。 */
    public record SelfInfo(String username, String email, String className) {
    }
}
