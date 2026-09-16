package com.paperpulse.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试用的极简 HTTP 客户端,包一层 JDK 自带的 {@link HttpClient}。
 *
 * <p><b>为什么不用 Spring 的测试客户端:</b>{@code TestRestTemplate} 在 Spring Boot 4 中已被移除。
 * 改用 JDK 自带的 {@code HttpClient} 反而更稳妥 —— 它不随 Spring 版本变动,而且默认就满足测试需要:
 * <ul>
 *   <li><b>不跟随重定向</b> —— 3xx 原样暴露,不会被悄悄变成 200</li>
 *   <li><b>不对 4xx/5xx 抛异常</b> —— 断言 401/404 时不会先炸在客户端</li>
 * </ul>
 *
 * <p>刻意固定 HTTP/1.1:明文连接下 JDK 客户端会尝试 h2c 升级,虽然 Tomcat 默认不支持、
 * 最终会回落,但测试里没必要引入这个不确定性。
 */
public class ApiClient {

    private static final String AUTHORIZATION = "Authorization";

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)   // 见类注释:避免 h2c 升级带来的不确定性
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public ApiClient(String baseUrl, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    /** 一次响应:状态码 + 响应头 + 已解析的 JSON 体。 */
    public record Response(int status, HttpResponse<String> raw, JsonNode body) {

        /** 取响应头,不存在返回 null(方便直接 assertThat(...).isEqualTo(...))。 */
        public String header(String name) {
            return raw.headers().firstValue(name).orElse(null);
        }

        /** 字段是否存在 —— 用于断言"响应里没有 password"。 */
        public boolean has(String field) {
            return body != null && body.has(field);
        }

        public JsonNode at(String field) {
            return body == null ? null : body.get(field);
        }

        public String text(String field) {
            JsonNode node = at(field);
            return node == null || node.isNull() ? null : node.asString();
        }

        /** 断言失败时贴出原始响应体,免得只看到一个光秃秃的状态码。 */
        public String describe() {
            return "HTTP " + status + " body=" + raw.body();
        }
    }

    // ── 便捷方法 ──────────────────────────────────────────────

    public Response get(String path) {
        return get(path, null);
    }

    public Response get(String path, String bearerToken) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (bearerToken != null) {
            headers.put(AUTHORIZATION, "Bearer " + bearerToken);
        }
        return exchange("GET", path, headers, null);
    }

    /** 发一个 JSON 请求体。 */
    public Response postJson(String path, String jsonBody) {
        Map<String, String> headers = Map.of("Content-Type", "application/json");
        return exchange("POST", path, headers, jsonBody);
    }

    /**
     * 原样发送指定的 Content-Type 与请求体。
     *
     * <p>用于测试"请求体不是合法 JSON"和"Content-Type 不支持"这类**故意畸形**的请求 ——
     * 这些场景没法用上面那些带校验的便捷方法构造。
     */
    public Response postRaw(String path, String contentType, String body) {
        return exchange("POST", path, Map.of("Content-Type", contentType), body);
    }

    /** 自定义请求头,用于测试 {@code Authorization} 的各种写法。 */
    public Response exchange(String method, String path, Map<String, String> headers, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30));

        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8);

        builder.method(method, publisher);
        headers.forEach(builder::setHeader);

        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response, parse(response.body()));
        } catch (IOException ex) {
            throw new IllegalStateException("请求 " + method + " " + path + " 失败(服务是否已启动?)", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求 " + method + " " + path + " 被中断", ex);
        }
    }

    /** 响应体不是 JSON 时返回 null —— 不要因为解析失败掩盖掉真正的状态码断言。 */
    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
