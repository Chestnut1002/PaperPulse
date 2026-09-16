package com.paperpulse.support;

import com.paperpulse.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试基类:起一个**真实的 Spring 容器 + 真实的 Tomcat**,用真实 HTTP 打过去。
 *
 * <p><b>为什么不用 MockMvc:</b>MockMvc 不经过 Servlet 容器,而这次要验证的东西有一部分
 * 恰恰是容器层面的行为 —— 比如错误转发到 {@code /error} 时的再分发、认证入口点写响应体的时机。
 * 用真实端口才能真正覆盖到,也才和当初的验收跑法一致。
 *
 * <p>数据库接真实 MySQL 的独立库 {@code paperpulse_test}。不用 H2 是有原因的:
 * 用户名大小写不敏感来自 MySQL 的 {@code utf8mb4_unicode_ci} 排序规则,H2 上会得出相反结论,
 * 那样测试会把一个与生产不符的行为固化下来。
 *
 * <p>profile 同时激活 {@code test} 与 {@code local}:前者提供测试库地址与建表策略(入库),
 * 后者提供本机数据库口令(不入库)。新克隆的仓库没有 {@code application-local.yml} 时
 * Spring 会静默跳过,改用环境变量 {@code DB_PASSWORD} 即可。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "local"})
@ContextConfiguration(initializers = TestDatabaseGuard.class)
public abstract class AbstractIntegrationTest {

    /** 随机端口,避免与本机正在运行的 8080 实例撞车。 */
    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtProperties jwtProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    protected ApiClient api;
    protected TokenForger forger;

    @BeforeEach
    void setUp() {
        api = new ApiClient("http://localhost:" + port, objectMapper);
        forger = new TokenForger(jwtProperties.secret(), objectMapper);

        // 每个用例从空表开始。用户名在用例之间会重复使用,不清理会互相干扰
        // (比如"用户名不存在应返回 401"被上一个用例建出来的用户破坏)。
        //
        // 顺序有讲究:先删引用别人的表,再删被引用的表。论文尤其重要 ——
        // 论文表的唯一约束是 (来源, 外部 ID),上个用例留下的论文会让
        // "首次提交应新建一行"的用例变成"已存在",而且它不是外键,不会自己级联。
        // 当前没有外键,顺序不影响执行,但保持这个习惯,将来真加了外键就不用回来改。
        jdbcTemplate.execute("DELETE FROM paper_favorite");
        jdbcTemplate.execute("DELETE FROM paper_read_history");
        jdbcTemplate.execute("DELETE FROM paper_rating");
        jdbcTemplate.execute("DELETE FROM paper");
        jdbcTemplate.execute("DELETE FROM user_interest");
        jdbcTemplate.execute("DELETE FROM users");
    }

    // ── 子类复用的小工具 ──────────────────────────────────────

    protected ApiClient.Response register(String username, String email, String password) {
        return api.postJson("/api/auth/register", json(Map.of(
                "username", username,
                "email", email,
                "password", password)));
    }

    /**
     * 注册一个用户并断言成功,返回新用户的 id。
     *
     * <p>断言放在这里,是为了让用例主体只关注它真正要验证的那件事。
     */
    protected long registerOk(String username, String email, String password) {
        ApiClient.Response response = register(username, email, password);
        assertThat(response.status()).as("注册应成功:%s", response.describe()).isEqualTo(201);
        return response.at("id").asLong();
    }

    /** 登录并取出 token。 */
    protected String loginAndGetToken(String username, String password) {
        ApiClient.Response response = api.postJson("/api/auth/login", json(Map.of(
                "username", username,
                "password", password)));
        assertThat(response.status()).as("登录应成功:%s", response.describe()).isEqualTo(200);
        return response.text("token");
    }

    /** 用 ObjectMapper 拼 JSON,而不是字符串拼接 —— 用例里会出现带引号的畸形输入。 */
    protected String json(Map<String, ?> body) {
        return objectMapper.writeValueAsString(body);
    }
}
