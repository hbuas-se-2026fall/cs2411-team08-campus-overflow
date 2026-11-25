package com.campusoverflow.reputation.interfaces;

import com.campusoverflow.reputation.application.LedgerEntryView;
import com.campusoverflow.reputation.application.PointsQueryService;
import com.campusoverflow.reputation.application.PointsView;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 「我的积分」端点：me 由 Actor 解析，需登录；不暴露他人积分。 */
@Tag(name = "积分 Points")
@RestController
@RequestMapping("/api/v1/users/me/points")
public class PointsController {

    private final PointsQueryService queries;

    public PointsController(PointsQueryService queries) {
        this.queries = queries;
    }

    @Operation(summary = "我的积分余额（可用、冻结、待追回）")
    @GetMapping
    public PointsView balance(@Parameter(hidden = true) Actor actor) {
        return queries.balance(actor);
    }

    @Operation(summary = "我的积分流水（仅 POINTS 币种）")
    @GetMapping("/ledger")
    public PageResult<LedgerEntryView> history(@Parameter(hidden = true) Actor actor,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return queries.history(actor, new PageRequest(page, size));
    }
}
