package com.paperpulse.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一异常出口。
 *
 * <p>所有错误响应形状一致:{@code timestamp / status / error / message},校验失败时额外带 {@code fieldErrors}。
 * 前端只需写一套错误处理逻辑。
 *
 * <p><b>为什么继承 {@link ResponseEntityExceptionHandler}</b>:Spring MVC 自己会抛一批异常
 * (路径不存在、方法不支持、Content-Type 不支持、请求体不是合法 JSON……),每一个都自带正确的
 * HTTP 状态码。如果只写一个 {@code @ExceptionHandler(Exception.class)} 兜底,这些异常会全部被
 * 当成 500 —— 把客户端的错报成服务端的错,还会为一次普通的 404 打出一整条堆栈。
 * 父类已经把这些异常逐个映射好了,且它们最终都汇聚到 {@link #handleExceptionInternal},
 * 因此只需覆盖那一个方法就能统一响应格式。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 常见客户端错误的中文提示。未列出的状态码回落到 HTTP 标准短语,避免漏写导致空消息。 */
    private static final Map<Integer, String> CLIENT_ERROR_MESSAGES = Map.of(
            HttpStatus.BAD_REQUEST.value(), "请求参数有误",
            HttpStatus.NOT_FOUND.value(), "请求的资源不存在",
            HttpStatus.METHOD_NOT_ALLOWED.value(), "不支持的请求方法",
            HttpStatus.NOT_ACCEPTABLE.value(), "无法生成客户端可接受的响应格式",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "不支持的 Content-Type"
    );

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException ex) {
        return build(ex.getStatus(), ex.getMessage(), null);
    }

    /**
     * {@code @Valid} 校验失败。
     *
     * <p>这里<b>覆盖父类方法</b>而不是另加一个 {@code @ExceptionHandler}:父类已经有处理
     * {@code MethodArgumentNotValidException} 的方法,再声明一个会让 Spring 启动时报
     * "Ambiguous @ExceptionHandler method mapped"。父类实现不返回 {@code fieldErrors},所以必须覆盖。
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        // 用 LinkedHashMap 而非 HashMap,保证字段顺序与声明顺序一致
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "参数校验失败", fieldErrors);
    }

    /**
     * Spring MVC 全部内置异常的公共出口 —— 父类每个 {@code handleXxx} 最后都会调到这里。
     *
     * <p>覆盖它,等于一次性把 400 / 404 / 405 / 406 / 415 等十几类异常都套上本项目的统一响应格式。
     *
     * <p>{@code headers} 必须原样透传:父类在 405 时会往里塞 {@code Allow}(列出该路径支持的方法),
     * 而 RFC 9110 §15.5.6 要求 405 响应必须带这个头。漏掉它只影响状态码断言察觉不到的地方。
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            return build(statusCode.value(), "请求处理失败", null, headers);
        }
        return build(status.value(),
                CLIENT_ERROR_MESSAGES.getOrDefault(status.value(), status.getReasonPhrase()), null, headers);
    }

    /** 兜底:未被预料到的异常。必须记日志,否则线上排查无从下手。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("未预期的异常", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误", null);
    }

    private ResponseEntity<Object> build(HttpStatus status, String message, Map<String, String> fieldErrors) {
        return build(status.value(), message, fieldErrors, null);
    }

    private ResponseEntity<Object> build(int status, String message, Map<String, String> fieldErrors,
                                         HttpHeaders headers) {
        HttpStatus resolved = HttpStatus.resolve(status);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status);
        // 非标准状态码时 resolve 返回 null,给个兜底字符串而不是 null
        body.put("error", resolved == null ? "Error" : resolved.getReasonPhrase());
        body.put("message", message);
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            body.put("fieldErrors", fieldErrors);
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (headers != null) {
            builder.headers(headers);
        }
        return builder.body(body);
    }
}
