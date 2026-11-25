package com.campusoverflow.reputation.interfaces;

import com.campusoverflow.reputation.application.BudgetView;
import com.campusoverflow.reputation.application.CourseBudgetService;
import com.campusoverflow.shared.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程积分预算端点。管理员调整走 /api/v1/admin/**，由 SecurityConfig 的 hasRole("ADMIN") 守护；
 * 查询端点由应用层再按课程成员/管理者做细粒度授权。
 */
@Tag(name = "课程预算 Course Budget")
@RestController
public class CourseBudgetController {

    private final CourseBudgetService budgets;

    public CourseBudgetController(CourseBudgetService budgets) {
        this.budgets = budgets;
    }

    @Operation(summary = "课程积分预算（可用、冻结、最近流水）")
    @GetMapping("/api/v1/courses/{courseId}/points-budget")
    public BudgetView budget(@Parameter(hidden = true) Actor actor, @PathVariable long courseId) {
        return budgets.budget(courseId, actor);
    }

    @Operation(summary = "管理员分配/回收课程预算（记录增减、来源、操作者、原因）")
    @PostMapping("/api/v1/admin/courses/{courseId}/budget-adjustments")
    public ResponseEntity<Map<String, Long>> adjust(@Parameter(hidden = true) Actor actor,
                                                    @PathVariable long courseId,
                                                    @Valid @RequestBody BudgetAdjustmentRequest req) {
        budgets.adjust(actor, courseId, req.delta(), req.reason(), req.commandId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("courseId", courseId));
    }

    public record BudgetAdjustmentRequest(@NotNull Integer delta, @NotBlank String reason, String commandId) {
    }
}
