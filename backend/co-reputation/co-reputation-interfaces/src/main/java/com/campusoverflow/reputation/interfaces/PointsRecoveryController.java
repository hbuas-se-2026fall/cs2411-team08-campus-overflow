package com.campusoverflow.reputation.interfaces;

import com.campusoverflow.reputation.application.PointsRecoveryService;
import com.campusoverflow.reputation.application.PointsRecoveryView;
import com.campusoverflow.reputation.domain.PointsRecovery;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 撤销后积分待追回的复核端点（/api/v1/admin/** 由 SecurityConfig 守护，应用层再校验 ADMIN）。 */
@Tag(name = "积分追回 Points Recovery")
@RestController
@RequestMapping("/api/v1/admin/points-recoveries")
public class PointsRecoveryController {

    private final PointsRecoveryService recoveries;

    public PointsRecoveryController(PointsRecoveryService recoveries) {
        this.recoveries = recoveries;
    }

    @Operation(summary = "待追回记录列表（含实时缺口）")
    @GetMapping
    public PageResult<PointsRecoveryView> pending(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return recoveries.pending(new PageRequest(page, size));
    }

    @Operation(summary = "复核待追回记录（只标记结论，不自动扣款）")
    @PostMapping("/{id}/resolve")
    public PointsRecoveryView resolve(@Parameter(hidden = true) Actor actor, @PathVariable long id,
                                      @Valid @RequestBody ResolveRequest req) {
        return recoveries.resolve(id, actor, req.decision(), req.reason());
    }

    public record ResolveRequest(@NotNull PointsRecovery.Decision decision, String reason) {
    }
}
