package com.campusoverflow.bootstrap.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 内部服务调用认证（B01）：保护 {@code /api/internal/**}，只接受携带
 * {@code X-Internal-Token} 且与配置一致的请求（AI 语义服务等可信内部调用方）。
 * <ul>
 *   <li>令牌经环境变量注入（{@code CO_INTERNAL_TOKEN}），生产环境未配置则应用启动失败；</li>
 *   <li>使用常量时间比较，避免时序侧信道；</li>
 *   <li>浏览器请求永远不应携带此令牌；部署上 Nginx 不向公网转发 {@code /api/internal/} 路径（纵深防御）。</li>
 * </ul>
 */
public class InternalAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    private static final String PROTECTED_PREFIX = "/api/internal/";

    private final String expectedToken;
    private final ObjectMapper objectMapper;

    public InternalAuthFilter(String expectedToken, ObjectMapper objectMapper) {
        this.expectedToken = expectedToken;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided == null || !constantTimeEquals(provided, expectedToken)) {
            writeProblem(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private void writeProblem(HttpServletResponse response) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "Unauthorized");
        body.put("status", 401);
        body.put("detail", "内部接口拒绝访问");
        body.put("code", "AUTH-401-INTERNAL");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/problem+json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
