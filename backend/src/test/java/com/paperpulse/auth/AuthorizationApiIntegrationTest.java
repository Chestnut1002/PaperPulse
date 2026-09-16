package com.paperpulse.auth;

import com.paperpulse.support.AbstractIntegrationTest;
import com.paperpulse.support.ApiClient;
import com.paperpulse.support.TokenForger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F4:受保护接口认不认 token。
 *
 * <p>核心是把三种失败区分开:
 * <ul>
 *   <li><b>没带 token</b> —— 未认证</li>
 *   <li><b>token 被改过 / 已过期</b> —— 签名或时效不过关</li>
 *   <li><b>token 完全合法但用户不存在</b> —— 签名有效 ≠ 应该放行</li>
 * </ul>
 * 第三种最容易漏:签名校验通过就放行,会让已注销用户的 token 继续可用。
 */
@DisplayName("F4 受保护接口鉴权")
class AuthorizationApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "secret123";
    private static final String ME = "/api/users/me";

    /** 带一个自定义的 Authorization 值发请求 —— 用来覆盖各种写法。 */
    private ApiClient.Response getWithRawAuthorization(String authorizationHeaderValue) {
        return api.exchange("GET", ME, Map.of("Authorization", authorizationHeaderValue), null);
    }

    @Test
    @DisplayName("F4-1 不带 Authorization 返回 401,且响应体是统一的 JSON 格式")
    void missingTokenIsUnauthorized() {
        ApiClient.Response response = api.get(ME);

        assertThat(response.status()).as(response.describe()).isEqualTo(401);

        // 401 由 RestAuthenticationEntryPoint 写出,格式必须与 GlobalExceptionHandler 一致,
        // 否则前端得为 401 单独写一套解析逻辑。
        assertThat(response.header("Content-Type")).contains("application/json");
        assertThat(response.body()).as("不能是空 body").isNotNull();
        assertThat(response.at("status").asInt()).isEqualTo(401);
        assertThat(response.has("timestamp")).isTrue();
        assertThat(response.has("error")).isTrue();
        assertThat(response.text("message")).isNotBlank();
    }

    @Test
    @DisplayName("F4-2 Bearer 后面跟乱码返回 401")
    void garbageTokenIsUnauthorized() {
        assertThat(api.get(ME, "abc").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("F4-3 合法 token 的签名被改动后返回 401")
    void tamperedSignatureIsRejected() {
        registerOk("alice", "alice@example.com", PASSWORD);
        String token = loginAndGetToken("alice", PASSWORD);

        String[] parts = TokenForger.split(token);
        String tampered = parts[0] + "." + parts[1] + "." + TokenForger.flipBit(parts[2]);

        assertThat(tampered)
                .as("改动必须真的落在签名数据上,否则这条用例什么都没验证")
                .isNotEqualTo(token);
        assertThat(api.get(ME, tampered).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("F4-4 用真实密钥签一个已过期的 token 返回 401")
    void expiredTokenIsRejected() {
        registerOk("alice", "alice@example.com", PASSWORD);

        // 签名是真的、密钥也是对的 —— 唯一的问题是有效期已过。
        String expired = forger.forge(1L, "alice",
                Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));

        assertThat(api.get(ME, expired).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("F4-5 真实登录拿到的 token 可以访问,返回本人信息且不含密码")
    void validTokenReturnsCurrentUser() {
        long userId = registerOk("alice", "alice@example.com", PASSWORD);
        String token = loginAndGetToken("alice", PASSWORD);

        ApiClient.Response response = api.get(ME, token);

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(response.at("id").asLong()).isEqualTo(userId);
        assertThat(response.text("username")).isEqualTo("alice");
        assertThat(response.has("password")).isFalse();
    }

    @Test
    @DisplayName("F4-6 漏掉 Bearer 前缀返回 401")
    void tokenWithoutBearerPrefixIsRejected() {
        registerOk("alice", "alice@example.com", PASSWORD);
        String token = loginAndGetToken("alice", PASSWORD);

        assertThat(getWithRawAuthorization(token).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("F4-7 scheme 全小写 bearer 也接受(RFC 7235 §2.1:认证 scheme 大小写不敏感)")
    void bearerSchemeIsCaseInsensitive() {
        registerOk("alice", "alice@example.com", PASSWORD);
        String token = loginAndGetToken("alice", PASSWORD);

        assertThat(getWithRawAuthorization("bearer " + token).status())
                .as("认证 scheme 按规范是不区分大小写的")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("F4-8 用真实密钥签一个 sub 指向不存在用户的合法 token 返回 401")
    void validSignatureForMissingUserIsRejected() {
        // 签名校验会通过 —— 这正是不查库就会漏掉的那一类:token 一签发就无法撤回,
        // 但只要每次访问都回查用户,用户被删除后其 token 立刻失效。
        String ghost = forger.forge(999_999L, "ghost", Instant.now(), Instant.now().plusSeconds(3600));

        assertThat(api.get(ME, ghost).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("F4-9 回归:登录接口不带 token 仍然可以访问(过滤器没把入口堵死)")
    void loginEndpointStaysPublic() {
        registerOk("alice", "alice@example.com", PASSWORD);

        ApiClient.Response response = api.postJson("/api/auth/login",
                json(Map.of("username", "alice", "password", PASSWORD)));

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
    }
}
