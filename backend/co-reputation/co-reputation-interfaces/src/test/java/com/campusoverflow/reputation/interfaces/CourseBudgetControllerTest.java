package com.campusoverflow.reputation.interfaces;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusoverflow.reputation.application.BudgetAdjustmentView;
import com.campusoverflow.reputation.application.BudgetView;
import com.campusoverflow.reputation.application.CourseBudgetService;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** B06 课程预算端点：路由、Actor 注入、请求体到用例参数的映射。 */
class CourseBudgetControllerTest {

    private static final Actor ADMIN = new Actor(99, "管理员", Role.ADMIN);
    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");

    private CourseBudgetService budgets;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        budgets = mock(CourseBudgetService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CourseBudgetController(budgets))
                .setCustomArgumentResolvers(new FixedActorResolver(ADMIN))
                .build();
    }

    @Test
    void budgetEndpointReturnsConservedBalancesAndRecentAdjustments() throws Exception {
        when(budgets.budget(eq(1L), any())).thenReturn(new BudgetView(1, "数据结构", 450, 50, 500,
                List.of(new BudgetAdjustmentView(1, 500, "ADMIN_ALLOCATION", 99, "期初分配", "cmd-1", NOW))));

        mvc.perform(get("/api/v1/courses/1/points-budget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(1))
                .andExpect(jsonPath("$.availablePoints").value(450))
                .andExpect(jsonPath("$.frozenPoints").value(50))
                .andExpect(jsonPath("$.totalPoints").value(500))
                .andExpect(jsonPath("$.recentAdjustments[0].source").value("ADMIN_ALLOCATION"));

        verify(budgets).budget(eq(1L), any());
    }

    @Test
    void adminAdjustmentEndpointMapsRequestBody() throws Exception {
        when(budgets.adjust(any(), eq(1L), eq(300), eq("期中追加"), eq("cmd-2")))
                .thenReturn(new BudgetView(1, "数据结构", 300, 0, 300, List.of()));

        mvc.perform(post("/api/v1/admin/courses/1/budget-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":300,\"reason\":\"期中追加\",\"commandId\":\"cmd-2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseId").value(1));

        verify(budgets).adjust(any(), eq(1L), eq(300), eq("期中追加"), eq("cmd-2"));
    }

    @Test
    void blankReasonIsRejectedByValidation() throws Exception {
        mvc.perform(post("/api/v1/admin/courses/1/budget-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":100,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
