package com.paperpulse.auth;

import com.paperpulse.support.AbstractIntegrationTest;
import com.paperpulse.support.ApiClient;
import com.paperpulse.support.TokenForger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F3:登录签发 JWT。
 *
 * <p>重点不在"能登录",而在 token 本身**是不是一个符合规范、且确实由本服务密钥签发的 JWT**。
 * 因此这里会解开 token 逐字段核对,并用密钥独立复算一遍签名。
 */
@DisplayName("F3 登录签发 JWT")
class AuthApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "secret123";

    private ApiClient.Response login(String username, String password) {
        return api.postJson("/api/auth/login", json(Map.of("username", username, "password", password)));
    }

    @Test
    @DisplayName("F3-1 正确凭据登录:返回 token / Bearer / 有效期,且响应不含密码")
    void loginWithValidCredentials() {
        registerOk("alice", "alice@example.com", PASSWORD);

        ApiClient.Response response = login("alice", PASSWORD);

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(response.text("token")).isNotBlank();
        assertThat(response.text("tokenType")).isEqualTo("Bearer");
        assertThat(response.at("expiresIn").asLong())
                .as("响应里的有效期应与配置 jwt.expiration-seconds 一致")
                .isEqualTo(jwtProperties.expirationSeconds());

        JsonNode user = response.at("user");
        assertThat(user).as("登录响应应带上用户信息").isNotNull();
        assertThat(user.get("username").asString()).isEqualTo("alice");
        assertThat(user.has("password"))
                .as("响应里绝不能出现 password —— 哪怕是哈希也不行")
                .isFalse();
    }

    @Test
    @DisplayName("F3-2 密码错误返回 401")
    void loginWithWrongPassword() {
        registerOk("alice", "alice@example.com", PASSWORD);

        ApiClient.Response response = login("alice", "wrong-password");

        assertThat(response.status()).as(response.describe()).isEqualTo(401);
        assertThat(response.text("message")).isEqualTo("用户名或密码错误");
    }

    @Test
    @DisplayName("F3-3 用户名不存在返回 401,且文案与密码错误完全相同(防用户名枚举)")
    void unknownUsernameIsIndistinguishableFromWrongPassword() {
        registerOk("alice", "alice@example.com", PASSWORD);

        String wrongPassword = login("alice", "wrong-password").text("message");
        String unknownUser = login("ghost", "wrong-password").text("message");

        // 不硬编码文案,而是断言两条路径**彼此一致**。
        // 只要两句提示有任何差别,攻击者就能靠它把系统里有哪些用户名一个个筛出来。
        assertThat(unknownUser).isEqualTo(wrongPassword);
    }

    @Test
    @DisplayName("F3-4 用户名/密码为空返回 400,并逐字段说明原因")
    void blankCredentialsAreRejected() {
        ApiClient.Response response = login("", "");

        assertThat(response.status()).as(response.describe()).isEqualTo(400);
        JsonNode fieldErrors = response.at("fieldErrors");
        assertThat(fieldErrors).as("400 响应应带上 fieldErrors").isNotNull();
        assertThat(fieldErrors.has("username")).isTrue();
        assertThat(fieldErrors.has("password")).isTrue();
    }

    @Test
    @DisplayName("F3-5 用户名带 SQL 注入串返回 401 而不是 500(证明是参数化查询)")
    void sqlInjectionAttemptIsTreatedAsPlainText() {
        ApiClient.Response response = login("' OR '1'='1", "whatever");

        // 500 说明拼接了 SQL;401 说明它只是被当成一个普通(不存在的)用户名字面量。
        assertThat(response.status()).as(response.describe()).isEqualTo(401);
    }

    @Test
    @DisplayName("F3-6 用户名大小写不敏感:Alice 可以登进 alice 的账号")
    void usernameIsCaseInsensitive() {
        registerOk("alice", "alice@example.com", PASSWORD);

        ApiClient.Response response = login("Alice", PASSWORD);

        // 这个行为**不是 Java 代码决定的**,而是 MySQL 列排序规则 utf8mb4_unicode_ci(_ci = case
        // insensitive)。若此处变为 401,说明列的排序规则被改成了 _bin/_cs —— 详见 User.java 的注释。
        // 本用例跑在真实 MySQL 上正是为了覆盖这件事:H2 的默认行为相反,会把错的结论固化下来。
        assertThat(response.status())
                .as("大小写不敏感来自数据库排序规则;若变成 401 请检查列的 collation")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("F3-10 注册 ALICE 应被拒(与 F3-6 自洽:不能出现两个只差大小写的账号)")
    void registeringCaseVariantIsRejected() {
        registerOk("alice", "alice@example.com", PASSWORD);

        ApiClient.Response response = register("ALICE", "alice2@example.com", PASSWORD);

        // 若这里放行,就会出现 alice 与 ALICE 两个账号,而由于登录大小写不敏感,
        // 后注册的那个永远登不进去 —— 注册查重与登录校验必须用同一套规则。
        assertThat(response.status()).as(response.describe()).isEqualTo(409);
        assertThat(response.text("message")).isEqualTo("用户名已被占用");
    }

    @Test
    @DisplayName("F3-7 解开 token:alg=HS512、sub 为用户 id、有效期与配置一致")
    void tokenPayloadIsCorrect() {
        long userId = registerOk("alice", "alice@example.com", PASSWORD);
        String token = loginAndGetToken("alice", PASSWORD);

        assertThat(forger.decodeHeader(token).get("alg").asString()).isEqualTo("HS512");

        JsonNode payload = forger.decodePayload(token);
        assertThat(payload.get("sub").asString())
                .as("JWT 规范要求 sub 是字符串")
                .isEqualTo(String.valueOf(userId));

        long lifetime = payload.get("exp").asLong() - payload.get("iat").asLong();
        assertThat(lifetime).isEqualTo(jwtProperties.expirationSeconds());
    }

    @Test
    @DisplayName("F3-8 用密钥独立复算 HMAC 签名,逐字节一致;篡改载荷后立刻对不上")
    void signatureCanBeReproducedIndependently() {
        registerOk("alice", "alice@example.com", PASSWORD);
        String token = loginAndGetToken("alice", PASSWORD);
        String[] parts = TokenForger.split(token);

        // 关键:不是"服务端说这个 token 有效",而是我用密钥自己算一遍,再和服务端的签名比对。
        // 若测试也用 jjwt 来验签,就变成用被验证的实现验证它自己 —— 两边会一起错。
        assertThat(parts[2])
                .as("独立复算的签名应与服务端签发的完全一致")
                .isEqualTo(forger.signToBase64(parts[0] + "." + parts[1]));

        String tampered = TokenForger.flipBit(parts[1]);
        assertThat(forger.signToBase64(parts[0] + "." + tampered))
                .as("载荷改动后,签名必须不再匹配")
                .isNotEqualTo(parts[2]);
    }

    @Test
    @DisplayName("F3-9 计时侧信道:用户名存在与否,响应耗时接近")
    void loginTimingDoesNotLeakUserExistence() {
        registerOk("alice", "alice@example.com", PASSWORD);

        // 预热:首次请求要付连接建立与 JIT 的代价,不预热会把差值算到侧信道上
        login("alice", "wrong-password");
        login("ghost", "wrong-password");

        long existingUser = medianMillis(5, () -> login("alice", "wrong-password"));
        long missingUser = medianMillis(5, () -> login("ghost", "wrong-password"));

        double ratio = (double) Math.max(existingUser, missingUser)
                / Math.max(1, Math.min(existingUser, missingUser));

        // 阈值放得很宽(3 倍):这条用例要抓的不是"差了几毫秒",而是"有没有跳过 BCrypt"。
        // 一旦 UserService 不再对不存在的用户跑那次哑比对,耗时会从 ~80ms 掉到 ~1ms,
        // 比值瞬间变成几十倍 —— 宽阈值足以捕获,又不会被机器负载抖动误报。
        assertThat(ratio)
                .as("用户存在 %dms vs 不存在 %dms;比值过大说明有一条路径跳过了 BCrypt", existingUser, missingUser)
                .isLessThan(3.0);
    }

    /** 取多次测量的中位数:中位数比平均值更不容易被偶发的 GC / 调度抖动带偏。 */
    private long medianMillis(int samples, Runnable action) {
        long[] elapsed = new long[samples];
        for (int i = 0; i < samples; i++) {
            long start = System.nanoTime();
            action.run();
            elapsed[i] = (System.nanoTime() - start) / 1_000_000;
        }
        Arrays.sort(elapsed);
        return elapsed[samples / 2];
    }
}
