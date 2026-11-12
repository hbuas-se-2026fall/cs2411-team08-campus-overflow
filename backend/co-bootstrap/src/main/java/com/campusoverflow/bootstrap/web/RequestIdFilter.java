package com.campusoverflow.bootstrap.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求追踪（B01）：为每个请求确定 Request-Id 并贯穿日志与响应。
 * <ul>
 *   <li>优先采用网关/上游传入的 {@code X-Request-Id}，否则生成 16 位随机 ID；</li>
 *   <li>写入 MDC（{@code requestId}），日志格式引用，便于把一次请求的所有日志串联；</li>
 *   <li>回写响应头 {@code X-Request-Id}，ProblemDetail 错误响应也附带，便于用户反馈定位；</li>
 *   <li>请求结束时清空 MDC，避免容器线程复用导致的串号。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_ACTOR_ID = "actorId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = isValid(incoming) ? incoming : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(MDC_REQUEST_ID, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
        response.setHeader(HEADER, requestId);
    }

    /** 只接受可控长度的合法字符，避免日志注入与超大头。 */
    private static boolean isValid(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_')) {
                return false;
            }
        }
        return true;
    }
}
