package com.paperpulse.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 未认证请求的统一出口。
 *
 * <p>Spring Security 默认的 {@code HttpStatusEntryPoint} 只回一个<b>空 body</b> 的 401,
 * 而 {@code GlobalExceptionHandler} 回的是 {@code {timestamp, status, error, message}}。
 * 同一个 401 有两种格式,前端就得写两套解析逻辑 —— 这个类把格式补齐,让"统一错误响应"这句话成立。
 *
 * <p>注入 Spring 容器里的 {@code ObjectMapper} 而不是 {@code new} 一个:日期序列化等配置
 * 由 Spring Boot 统一决定,这样两条路径输出的 {@code timestamp} 格式才完全一致。
 *
 * <p><b>注意包名是 {@code tools.jackson} 而非 {@code com.fasterxml.jackson}。</b>
 * Spring Boot 4 已迁到 Jackson 3,坐标与包名都换了;工程里那个
 * {@code com.fasterxml.jackson.core:jackson-databind} 是 jjwt 拖进来的 Jackson 2,
 * 且是 {@code runtime} 作用域 —— 运行期在、编译期看不见,照着老包名写会编译不过。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        // 不区分"没带 token"和"token 无效" —— 与 UserService 处理登录失败同理,少给攻击者一点线索。
        body.put("message", "未登录或登录凭证无效");

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
