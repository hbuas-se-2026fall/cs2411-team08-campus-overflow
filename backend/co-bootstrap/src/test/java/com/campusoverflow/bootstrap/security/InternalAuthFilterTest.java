package com.campusoverflow.bootstrap.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalAuthFilterTest {

    private static final String TOKEN = "secret-internal-token";
    private final InternalAuthFilter filter = new InternalAuthFilter(TOKEN, new ObjectMapper());

    @Test
    void 内部路径缺少令牌返回401ProblemJson() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/internal/rag/generate");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getContentAsString()).contains("AUTH-401-INTERNAL");
    }

    @Test
    void 内部路径令牌错误返回401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/internal/rag/generate");
        request.addHeader(InternalAuthFilter.HEADER, "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void 内部路径正确令牌放行() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/internal/rag/generate");
        request.addHeader(InternalAuthFilter.HEADER, TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] reached = {false};

        FilterChain chain = (req, res) -> reached[0] = true;
        filter.doFilter(request, response, chain);

        assertThat(reached[0]).isTrue();
    }

    @Test
    void 非内部路径直接放行不校验令牌() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/questions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] reached = {false};

        FilterChain chain = (req, res) -> reached[0] = true;
        filter.doFilter(request, response, chain);

        assertThat(reached[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
