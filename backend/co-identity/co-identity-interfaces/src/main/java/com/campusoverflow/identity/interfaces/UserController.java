package com.campusoverflow.identity.interfaces;

import com.campusoverflow.identity.application.ProfileView;
import com.campusoverflow.identity.application.UpdateProfileCommand;
import com.campusoverflow.identity.application.UserProfileService;
import com.campusoverflow.shared.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人主页接口。
 * GET /users/{id}/profile：公开字段为主，本人查看时附 self 私有块（email/username/className）。
 * PUT /users/me/profile：仅本人可改昵称/学院/班级，角色与认证状态不在此列。
 */
@Tag(name = "个人主页 Profile")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserProfileService profiles;

    public UserController(UserProfileService profiles) {
        this.profiles = profiles;
    }

    @Operation(summary = "个人主页（他人仅公开字段，本人附私有信息块）")
    @GetMapping("/{id}/profile")
    public ProfileView profile(@Parameter(hidden = true) Actor actor, @PathVariable long id) {
        return profiles.profileOf(actor.userId(), id);
    }

    @Operation(summary = "修改本人资料（仅开放昵称/学院/班级）")
    @PutMapping("/me/profile")
    public void updateMyProfile(@Parameter(hidden = true) Actor actor,
                                @Valid @RequestBody UpdateProfileRequest req) {
        profiles.updateMyProfile(actor, new UpdateProfileCommand(req.displayName(), req.college(),
                req.className()));
    }

    public record UpdateProfileRequest(
            @Size(min = 2, max = 20, message = "昵称长度应为 2–20 个字符") String displayName,
            @Size(max = 50, message = "学院最长 50 个字符") String college,
            @Size(max = 50, message = "班级最长 50 个字符") String className) {
    }
}
