package com.campusoverflow.reputation.interfaces;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusoverflow.reputation.application.LedgerEntryView;
import com.campusoverflow.reputation.application.PointsQueryService;
import com.campusoverflow.reputation.application.PointsView;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** B05-2 新端点：路由、me 主体解析、视图序列化（standalone MockMvc，不启动完整上下文）。 */
class PointsControllerTest {

    private static final Actor SELF = new Actor(2, "小红", Role.STUDENT);
    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");

    private PointsQueryService queries;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        queries = mock(PointsQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PointsController(queries))
                .setCustomArgumentResolvers(new FixedActorResolver())
                .build();
    }

    @Test
    void balanceEndpointReturnsPointsView() throws Exception {
        when(queries.balance(any())).thenReturn(new PointsView(2, 30, 5, 35, 0));

        mvc.perform(get("/api/v1/users/me/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.availablePoints").value(30))
                .andExpect(jsonPath("$.frozenPoints").value(5))
                .andExpect(jsonPath("$.totalPoints").value(35))
                .andExpect(jsonPath("$.pendingRecovery").value(0));

        verify(queries).balance(argThat(a -> a.userId() == 2));
    }

    @Test
    void historyEndpointReturnsPointsLedger() throws Exception {
        LedgerEntryView entry = new LedgerEntryView(1L, "ANSWER_ENDORSED", "教师认证", 5, "ANSWER", 50, NOW, null);
        when(queries.history(any(), any())).thenReturn(new PageResult<>(List.of(entry), 1, 1, 20));

        mvc.perform(get("/api/v1/users/me/points/ledger").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reason").value("ANSWER_ENDORSED"))
                .andExpect(jsonPath("$.items[0].delta").value(5))
                .andExpect(jsonPath("$.total").value(1));

        verify(queries).history(argThat((Actor a) -> a.userId() == 2),
                argThat((PageRequest p) -> p.page() == 1 && p.size() == 20));
    }

    /** 模拟 bootstrap 的 ActorArgumentResolver：把登录主体注入控制器参数。 */
    private static final class FixedActorResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == Actor.class;
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return SELF;
        }
    }
}
