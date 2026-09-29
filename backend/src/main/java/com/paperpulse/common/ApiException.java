package com.paperpulse.common;

import org.springframework.http.HttpStatus;

/**
 * 业务异常:携带 HTTP 状态码,由 {@link GlobalExceptionHandler} 统一转成响应体。
 *
 * <p>约定:"能预料到的失败"抛这个(用户名重复、密码错误……),
 * 未预料的异常则交给框架兜底为 500 —— 这样日志里剩下的异常都是真 bug。
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    /**
     * 上游服务返回了错误。
     *
     * <p>用 502 而不是 500:这让"我们的代码有 bug"和"依赖的服务出问题了"在日志与监控里区分得开 ——
     * 两者的排查方向完全不同。
     */
    public static ApiException badGateway(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, message);
    }

    /** 上游服务连不上。属于"暂时不可用",重试可能就好了,所以是 503 不是 500。 */
    public static ApiException serviceUnavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
