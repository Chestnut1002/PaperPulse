package com.paperpulse.common;

import com.paperpulse.support.AbstractIntegrationTest;
import com.paperpulse.support.ApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E:客户端错误的分类回归。
 *
 * <p><b>这一组用例的价值在于它们曾经全是 500。</b>根因是 {@code GlobalExceptionHandler} 里的
 * {@code @ExceptionHandler(Exception.class)} 兜底抢在 Spring MVC 自带的异常解析器之前,
 * 把所有内置异常都吞成了"服务器内部错误"。正常路径的测试完全发现不了这个问题 ——
 * 注册登录全都好好的,只有去问"如果请求本身是错的会怎样"才会撞上。
 *
 * <p>两个危害:一是把客户端的错报成服务端的错,前端没法区分"我请求错了"和"服务挂了";
 * 二是每来一个 404 就打一整条堆栈,真出事时日志已经被淹了。
 */
@DisplayName("E 客户端错误分类(修复前这五项均为 500)")
class ErrorHandlingIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "secret123";
    private static final String MISSING_PATH = "/api/definitely-not-a-real-path";

    @Test
    @DisplayName("E-1 已登录访问不存在的路径返回 404")
    void authenticatedRequestToMissingPathIsNotFound() {
        registerOk("alice", "alice@example.com", PASSWORD);
        String token = loginAndGetToken("alice", PASSWORD);

        ApiClient.Response response = api.get(MISSING_PATH, token);

        assertThat(response.status()).as(response.describe()).isEqualTo(404);
        assertThat(response.text("message")).isEqualTo("请求的资源不存在");
    }

    @Test
    @DisplayName("E-2 未登录访问不存在的路径返回 401 —— 不向匿名者暴露路径是否存在")
    void anonymousRequestToMissingPathIsUnauthorized() {
        // 先认证再谈资源存不存在:否则任何人都能靠 404 与 401 的差别探测出系统里有哪些接口。
        assertThat(api.get(MISSING_PATH).status())
                .as("匿名请求应在鉴权阶段就被拦下,而不是走到路由匹配")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("E-3 请求体不是合法 JSON 返回 400")
    void malformedJsonIsBadRequest() {
        ApiClient.Response response = api.postRaw("/api/auth/register", "application/json", "{\"username\":");

        assertThat(response.status()).as(response.describe()).isEqualTo(400);
    }

    @Test
    @DisplayName("E-4 GET 调只接受 POST 的接口返回 405,且带 Allow 头(RFC 9110 §15.5.6 要求必须带)")
    void wrongMethodReturnsMethodNotAllowedWithAllowHeader() {
        ApiClient.Response response = api.get("/api/auth/login");

        assertThat(response.status()).as(response.describe()).isEqualTo(405);
        assertThat(response.header("Allow"))
                .as("405 必须告诉客户端这个路径支持哪些方法,否则调用方只能靠猜")
                .isNotNull()
                .contains("POST");
    }

    @Test
    @DisplayName("E-5 Content-Type 不支持返回 415")
    void unsupportedContentTypeIsUnsupportedMediaType() {
        ApiClient.Response response = api.postRaw("/api/auth/register", "text/plain", "not json at all");

        assertThat(response.status()).as(response.describe()).isEqualTo(415);
    }

    @Test
    @DisplayName("E-6 直接访问 /error 返回 401,不泄露服务端信息")
    void errorEndpointIsNotPubliclyReadable() {
        // /error 是 Spring Boot 的兜底错误页。它没有任何理由对匿名者开放 ——
        // 早期版本为了"防止错误被改写"放行过它,实测证明那是一条走不到的路,已删除。
        ApiClient.Response response = api.get("/error");

        assertThat(response.status()).as(response.describe()).isEqualTo(401);
    }
}
