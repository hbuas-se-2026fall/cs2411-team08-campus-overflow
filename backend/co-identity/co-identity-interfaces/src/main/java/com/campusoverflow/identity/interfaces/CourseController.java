package com.campusoverflow.identity.interfaces;

import com.campusoverflow.identity.application.CourseMemberView;
import com.campusoverflow.identity.application.CourseService;
import com.campusoverflow.identity.application.CourseView;
import com.campusoverflow.identity.application.CreateCourseCommand;
import com.campusoverflow.identity.domain.CourseRole;
import com.campusoverflow.identity.domain.CourseStatus;
import com.campusoverflow.shared.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "课程 Course")
@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseService courses;

    public CourseController(CourseService courses) {
        this.courses = courses;
    }

    @Operation(summary = "课程列表（登录时附带我的课程角色）")
    @GetMapping
    public List<CourseView> list(@Parameter(hidden = true) Optional<Actor> actor) {
        return courses.listAll(actor.orElse(null));
    }

    @Operation(summary = "我加入的课程")
    @GetMapping("/mine")
    public List<CourseView> mine(@Parameter(hidden = true) Actor actor) {
        return courses.myCourses(actor.userId());
    }

    @Operation(summary = "创建课程（教师/管理员）")
    @PostMapping
    public ResponseEntity<Map<String, Long>> create(@Parameter(hidden = true) Actor actor,
                                                    @Valid @RequestBody CreateCourseRequest req) {
        long id = courses.create(actor, new CreateCourseCommand(req.code(), req.name(), req.term()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @Operation(summary = "加入课程（归档课程不可加入；曾被移除的成员可重新加入）")
    @PostMapping("/{courseId}/join")
    public ResponseEntity<Void> join(@Parameter(hidden = true) Actor actor, @PathVariable long courseId) {
        courses.join(actor, courseId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "退出课程（教师不可退出自己的课程）")
    @PostMapping("/{courseId}/leave")
    public ResponseEntity<Void> leave(@Parameter(hidden = true) Actor actor, @PathVariable long courseId) {
        courses.leave(actor, courseId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "课程成员名单（仅课程成员或管理员可查看）")
    @GetMapping("/{courseId}/members")
    public List<CourseMemberView> members(@Parameter(hidden = true) Actor actor,
                                          @PathVariable long courseId) {
        return courses.members(courseId, actor);
    }

    @Operation(summary = "调整成员角色（如任命助教，仅课程教师/管理员）")
    @PutMapping("/{courseId}/members/{userId}")
    public ResponseEntity<Void> assignRole(@Parameter(hidden = true) Actor actor, @PathVariable long courseId,
                                           @PathVariable long userId, @Valid @RequestBody AssignRoleRequest req) {
        courses.assignRole(actor, courseId, userId, req.role());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "移除成员（软删除，仅课程教师/管理员；教师本人不可被移除）")
    @DeleteMapping("/{courseId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@Parameter(hidden = true) Actor actor, @PathVariable long courseId,
                                             @PathVariable long userId) {
        courses.removeMember(actor, courseId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "课程状态调整（归档/重开，仅课程教师/管理员；归档后只读）")
    @PutMapping("/{courseId}/status")
    public ResponseEntity<Void> changeStatus(@Parameter(hidden = true) Actor actor, @PathVariable long courseId,
                                             @Valid @RequestBody ChangeStatusRequest req) {
        courses.changeStatus(actor, courseId, req.status());
        return ResponseEntity.noContent().build();
    }

    public record CreateCourseRequest(@NotBlank String code, @NotBlank String name, @NotBlank String term) {
    }

    public record AssignRoleRequest(@NotNull CourseRole role) {
    }

    public record ChangeStatusRequest(@NotNull CourseStatus status) {
    }
}
