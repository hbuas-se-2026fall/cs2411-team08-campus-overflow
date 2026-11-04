package com.campusoverflow.bootstrap.web;

import com.campusoverflow.identity.application.LoginThrottledException;
import com.campusoverflow.shared.domain.DomainException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一错误响应：RFC 9457 Problem Details + 业务错误码 code。
 * 领域异常自带建议状态码，接口层只做翻译，不做业务判断。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> domain(DomainException ex) {
        return problem(HttpStatus.valueOf(ex.suggestedStatus()), ex.code(), ex.getMessage());
    }

    /** 登录限流：429 + 剩余等待秒数，供前端展示等待信息（F02）。 */
    @ExceptionHandler(LoginThrottledException.class)
    public ResponseEntity<ProblemDetail> loginThrottled(LoginThrottledException ex) {
        ProblemDetail pd = body(HttpStatus.TOO_MANY_REQUESTS, ex.code(), ex.getMessage());
        pd.setProperty("retryAfterSeconds", ex.retryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(pd);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ProblemDetail> locked(LockedException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "AUTH-429", ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> unauthenticated(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "AUTH-401", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> forbidden(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "AUTH-403", "无权访问该资源");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> optimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "SYS-409-CONCURRENT", "内容已被他人修改，请刷新后重试");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException ex) {
        // 底层 cause 可能包含约束名/SQL 片段，只在 DEBUG 级别保留，避免生产日志泄露存储细节
        if (log.isDebugEnabled()) {
            log.debug("数据完整性冲突: {}", ex.getMostSpecificCause().getMessage());
        }
        return problem(HttpStatus.CONFLICT, "SYS-409-DUPLICATE", "操作冲突（可能是重复提交），请刷新后重试");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> badArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "SYS-400", "请求参数不合法");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> badType(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "SYS-400-PARAM", "请求参数类型不正确");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> constraint(ConstraintViolationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请求参数校验失败");
        pd.setProperty("code", "SYS-400-VALIDATION");
        pd.setProperty("errors", ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", String.valueOf(v.getPropertyPath()),
                        "message", v.getMessage() == null ? "不合法" : v.getMessage()))
                .toList());
        String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID);
        if (requestId != null) {
            pd.setProperty("requestId", requestId);
        }
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("未处理的异常", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "SYS-500", "服务器开小差了，请稍后重试");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请求参数校验失败");
        pd.setProperty("code", "SYS-400-VALIDATION");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(),
                        "message", f.getDefaultMessage() == null ? "不合法" : f.getDefaultMessage()))
                .toList();
        pd.setProperty("errors", errors);
        String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID);
        if (requestId != null) {
            pd.setProperty("requestId", requestId);
        }
        return ResponseEntity.badRequest().body(pd);
    }

    /** 未知路径：404，而不是落进兜底 500。 */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers,
                                                                    HttpStatusCode status, WebRequest request) {
        return withRequestIds(HttpStatus.NOT_FOUND, "SYS-404", "资源不存在");
    }

    /** 不支持的 HTTP 方法：405。 */
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                         HttpHeaders headers, HttpStatusCode status,
                                                                         WebRequest request) {
        return withRequestIds(HttpStatus.METHOD_NOT_ALLOWED, "SYS-405", "不支持的请求方法");
    }

    /** 请求体不可解析（JSON 格式错误等）：400。 */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return withRequestIds(HttpStatus.BAD_REQUEST, "SYS-400-BODY", "请求体格式不正确");
    }

    private static ResponseEntity<Object> withRequestIds(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status).body(body(status, code, detail));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status).body(body(status, code, detail));
    }

    private static ProblemDetail body(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID);
        if (requestId != null) {
            pd.setProperty("requestId", requestId);
        }
        return pd;
    }
}
