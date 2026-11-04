package com.campusoverflow.bootstrap.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void cleanUp() {
        MDC.clear();
    }

    @Test
    void 无传入头时生成新ID并回写响应头() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/questions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) ->
                assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isNotBlank();
        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotBlank();
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isNull(); // 请求结束后清理
    }

    @Test
    void 合法的传入头被采用() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/questions");
        request.addHeader(RequestIdFilter.HEADER, "gw-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("gw-abc-123");
    }

    @Test
    void 非法传入头被替换为生成的ID() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/questions");
        request.addHeader(RequestIdFilter.HEADER, "bad id with spaces\nand newline");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .isNotEqualTo("bad id with spaces\nand newline")
                .matches("[0-9a-f]{16}");
    }

    @Test
    void 异常路径也清理MDC() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/questions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            FilterChain chain = (req, res) -> {
                MDC.put(RequestIdFilter.MDC_ACTOR_ID, "42");
                throw new IllegalStateException("boom");
            };
            filter.doFilter(request, response, chain);
        } catch (ServletException | IOException | IllegalStateException expected) {
            // 预期异常：RuntimeException 会穿透过滤器向上传播
        }
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }
}
