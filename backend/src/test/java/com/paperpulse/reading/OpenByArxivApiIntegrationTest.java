package com.paperpulse.reading;

import com.paperpulse.support.AbstractIntegrationTest;
import com.paperpulse.support.ApiClient;
import com.paperpulse.support.StubAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 粘贴 arXiv 编号/链接直接打开论文。
 *
 * <p>这是精读最自然的入口 —— 用户手里已经有一篇想读的论文,不该被迫先搜一遍。
 */
class OpenByArxivApiIntegrationTest extends AbstractIntegrationTest {

    private static final StubAiService AI_STUB = new StubAiService();

    @DynamicPropertySource
    static void pointAtStub(DynamicPropertyRegistry registry) {
        registry.add("app.ai-service.base-url", AI_STUB::getBaseUrl);
    }

    private static final String FOUND = """
            {"found": true, "paper": {
              "source": "arxiv", "externalId": "2502.19271", "arxivId": "2502.19271",
              "doi": null, "title": "一篇 arXiv 论文", "authors": ["Wei Li"],
              "abstractText": "摘要", "publicationYear": 2025,
              "venue": null, "url": "https://arxiv.org/abs/2502.19271"}}
            """;

    private static final String NOT_FOUND = """
            {"found": false, "paper": null}
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetStub() {
        AI_STUB.reset();
    }

    private String registerAndLogin(String username) {
        registerOk(username, username + "@example.com", "secret123");
        return loginAndGetToken(username, "secret123");
    }

    private ApiClient.Response open(String token, String reference) {
        return api.postJson("/api/papers/from-arxiv", token, json(Map.of("reference", reference)));
    }

    private int paperCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM paper", Integer.class);
    }

    @Test
    @DisplayName("粘贴编号就能拿到可精读的论文")
    void opensPaperFromBareId() {
        String token = registerAndLogin("reader");
        AI_STUB.lookup().respondWith(200, FOUND);

        ApiClient.Response response = open(token, "2502.19271");

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(response.at("id").asLong()).isPositive();
        assertThat(response.text("title")).isEqualTo("一篇 arXiv 论文");
        assertThat(response.text("arxivId")).as("拿到本地 id 就是为了接着进精读").isEqualTo("2502.19271");
        assertThat(paperCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("粘贴链接也行 —— 用户不该被迫自己抠编号")
    void acceptsUrlToo() {
        String token = registerAndLogin("reader");
        AI_STUB.lookup().respondWith(200, FOUND);

        assertThat(open(token, "https://arxiv.org/abs/2502.19271v2").status()).isEqualTo(200);

        // 发出去的应当是抠出来的编号,而不是整条链接
        assertThat(AI_STUB.lookup().receivedBodies().get(0)).contains("2502.19271");
    }

    @Test
    @DisplayName("认不出编号时说清要粘什么,而不是报错")
    void unrecognisedReferenceExplainsWhatToPaste() {
        String token = registerAndLogin("reader");

        ApiClient.Response response = open(token, "https://example.com/some/paper");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("message")).contains("arXiv");
        assertThat(AI_STUB.lookup().receivedBodies()).as("认不出来就不该去麻烦上游").isEmpty();
    }

    @Test
    @DisplayName("编号不存在时返回 404")
    void unknownIdIsNotFound() {
        String token = registerAndLogin("reader");
        AI_STUB.lookup().respondWith(200, NOT_FOUND);

        ApiClient.Response response = open(token, "2502.99999");

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.text("message")).contains("2502.99999");
    }

    @Test
    @DisplayName("同一篇重复打开不会多存一行")
    void openingTwiceReusesTheRow() {
        String token = registerAndLogin("reader");
        AI_STUB.lookup().respondWith(200, FOUND);

        long first = open(token, "2502.19271").at("id").asLong();
        long second = open(token, "2502.19271").at("id").asLong();

        assertThat(second).isEqualTo(first);
        assertThat(paperCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("已以别的身份在库里的论文,打开时不会多存一行")
    void openingMergesIntoAnExistingRow() {
        String token = registerAndLogin("reader");
        // 先让这篇以 Crossref 的身份在库里(同一标题、同一作者、同一年)
        long existing = api.postJson("/api/papers", token, json(Map.of(
                "source", "crossref", "externalId", "10.1/x", "title", "一篇 arXiv 论文",
                "authors", List.of("Wei Li"), "publicationYear", 2025))).at("id").asLong();

        AI_STUB.lookup().respondWith(200, FOUND);
        long opened = open(token, "2502.19271").at("id").asLong();

        assertThat(opened).as("跨来源合并会认出是同一篇").isEqualTo(existing);
        assertThat(paperCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("未登录不能用")
    void requiresAuthentication() {
        assertThat(api.postJson("/api/papers/from-arxiv",
                json(Map.of("reference", "2502.19271"))).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("空内容返回 400")
    void blankReferenceIsRejected() {
        String token = registerAndLogin("reader");

        ApiClient.Response response = open(token, "");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.at("fieldErrors").get("reference")).isNotNull();
    }

    @Test
    @DisplayName("上游不可用时返回 503")
    void upstreamFailureIsMapped() {
        String token = registerAndLogin("reader");
        AI_STUB.lookup().respondWith(503, "{\"detail\":\"取 arXiv 元数据失败\"}");

        assertThat(open(token, "2502.19271").status()).isEqualTo(502);
    }
}
