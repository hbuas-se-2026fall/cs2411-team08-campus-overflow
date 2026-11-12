package com.campusoverflow.identity.application;

import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserRepository;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用例：个人主页的查看与本人资料的修改。
 * 公开字段与私有字段在同一视图里分块（{@code self} 仅本人非 null），保证他人永远拿不到
 * email/username/className，也拿不到余额与积分流水（属于声誉上下文，本服务不聚合）。
 */
@Service
public class UserProfileService {

    private final UserRepository users;

    public UserProfileService(UserRepository users) {
        this.users = users;
    }

    /** 修改本人允许字段（昵称/学院/班级）。昵称全局唯一，冲突返回 409。 */
    @Transactional
    public void updateMyProfile(Actor actor, UpdateProfileCommand cmd) {
        User u = load(actor.userId());
        if (users.existsByDisplayName(cmd.displayName())
                && users.findByDisplayName(cmd.displayName())
                        .map(existing -> existing.id() != u.id())
                        .orElse(false)) {
            throw new ConflictException("ID-2007", "该昵称已被使用");
        }
        u.updateProfile(actor, cmd.displayName(), cmd.college(), cmd.className());
        users.save(u);
    }

    /** 个人主页：本人查看附带私有信息块，他人只拿公开字段。 */
    @Transactional(readOnly = true)
    public ProfileView profileOf(long viewerId, long targetId) {
        User u = load(targetId);
        ProfileView.SelfInfo self = viewerId == targetId
                ? new ProfileView.SelfInfo(u.username(), u.email(), u.className())
                : null;
        return new ProfileView(u.id(), u.displayName(), u.role(), u.verified(), u.college(),
                u.createdAt(), self);
    }

    private User load(long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("ID-4001", "用户不存在"));
    }
}
