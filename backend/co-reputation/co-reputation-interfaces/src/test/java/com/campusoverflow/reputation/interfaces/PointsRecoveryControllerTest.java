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

import com.campusoverflow.reputation.application.PointsRecoveryService;
import com.campusoverflow.reputation.application.PointsRecoveryView;
import com.campusoverflow.reputation.domain.PointsRecovery;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** B06 待追回复核端点：分页列表与结论映射。 */
class PointsRecoveryControllerTest {

    private static final Actor ADMIN = new Actor(99, "管理员", Role.ADMIN);
    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");

    private PointsRecoveryService recoveries;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        recoveries = mock(PointsRecoveryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PointsRecoveryController(recoveries))
                .setCustomArgumentResolvers(new FixedActorResolver(ADMIN))
                .build();
    }

    @Test
    void pendingEndpointReturnsPageWithCurrentGap() throws Exception {
        when(recoveries.pending(any())).thenReturn(new PageResult<>(List.of(
                new PointsRecoveryView(1, 2, 5, "PENDING", "revoke-1", NOW, null, null, null, 5)), 1, 1, 20));

        mvc.perform(get("/api/v1/admin/points-recoveries").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].amount").value(5))
                .andExpect(jsonPath("$.items[0].currentGap").value(5))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));

        verify(recoveries).pending(any());
    }

    @Test
    void resolveEndpointMapsDecision() throws Exception {
        when(recoveries.resolve(eq(1L), any(), eq(PointsRecovery.Decision.WAIVED), eq("豁免")))
                .thenReturn(new PointsRecoveryView(1, 2, 5, "WAIVED", "revoke-1", NOW, NOW, 99L, "豁免", 5));

        mvc.perform(post("/api/v1/admin/points-recoveries/1/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"WAIVED\",\"reason\":\"豁免\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAIVED"))
                .andExpect(jsonPath("$.resolvedBy").value(99));

        verify(recoveries).resolve(eq(1L), any(), eq(PointsRecovery.Decision.WAIVED), eq("豁免"));
    }
}
