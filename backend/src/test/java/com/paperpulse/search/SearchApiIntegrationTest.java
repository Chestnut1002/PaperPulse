package com.paperpulse.search;

import com.paperpulse.common.ApiException;
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
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 自然语言检索接口(REQ-002)。
 *
 * <p>ai-service 用一个真实的 HTTP 桩服务替代(见 {@link StubAiService}) ——
 * 被测路径上除了"对面的服务是谁",其余全是真的:HTTP 调用、JSON 反序列化、状态码映射、落库。
 */
class SearchApiIntegrationTest extends AbstractIntegrationTest {

    private static final StubAiService AI_STUB = new StubAiService();

    @DynamicPropertySource
    static void pointAtStub(DynamicPropertyRegistry registry) {
        registry.add("app.ai-service.base-url", AI_STUB::getBaseUrl);
    }

    private static final String TWO_PAPERS = """
            {
              "query": "找对比学习在推荐系统里的应用",
              "plan": {
                "keywords": "contrastive learning recommendation",
                "yearFrom": 2024,
                "yearTo": null,
                "rationale": "拆成对比学习与推荐系统两个主题词"
              },
              "sourceLabel": "Semantic Scholar",
              "papers": [
                {
                  "source": "semantic_scholar", "externalId": "S2-001", "title": "论文一",
                  "authors": ["Alice", "Bob"], "abstractText": "摘要一",
                  "publicationYear": 2025, "venue": "NeurIPS",
                  "url": "https://example.com/1", "citationCount": 12
                },
                {
                  "source": "crossref", "externalId": "10.1000/xyz", "title": "论文二",
                  "authors": [], "abstractText": null,
                  "publicationYear": 2024, "venue": null, "url": null, "citationCount": 0
                }
              ]
            }
            """;

    private static final String EMPTY_RESULT = """
            {"query": "q", "plan": {"keywords": "k", "rationale": "r"},
             "sourceLabel": "Crossref", "papers": []}
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

    private ApiClient.Response search(String token, Map<String, ?> body) {
        return api.postJson("/api/papers/search", token, json(body));
    }

    // ── 正常路径 ──────────────────────────────────────────────

    @Test
    @DisplayName("检索结果落库,返回带本地 id 的论文")
    void searchPersistsPapers() {
        AI_STUB.respondWith(200, TWO_PAPERS);
        String token = registerAndLogin("searcher");

        ApiClient.Response response = search(token, Map.of("query", "找对比学习在推荐系统里的应用"));

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(response.text("sourceLabel")).isEqualTo("Semantic Scholar");

        JsonNode papers = response.at("papers");
        assertThat(papers.size()).isEqualTo(2);
        assertThat(papers.get(0).get("id").asLong()).isPositive();
        assertThat(papers.get(0).get("title").asString()).isEqualTo("论文一");
        assertThat(papers.get(0).get("authors").size()).isEqualTo(2);
        assertThat(papers.get(0).get("sourceDisplayName").asString()).isEqualTo("Semantic Scholar");
        // 第二个来源的中文名由后端查词表得来,前端不必自己维护一份映射
        assertThat(papers.get(1).get("sourceDisplayName").asString()).isEqualTo("Crossref");
    }

    @Test
    @DisplayName("把 Agent 的拆解结果一并返回,让'为什么这样搜'对用户可见")
    void returnsAgentPlan() {
        AI_STUB.respondWith(200, TWO_PAPERS);
        String token = registerAndLogin("searcher");

        ApiClient.Response response = search(token, Map.of("query", "找对比学习在推荐系统里的应用"));

        assertThat(response.text("keywords")).isEqualTo("contrastive learning recommendation");
        assertThat(response.text("rationale")).isEqualTo("拆成对比学习与推荐系统两个主题词");
    }

    @Test
    @DisplayName("检索到的论文可直接收藏 —— 客户端不必再提交任何元数据")
    void searchedPapersCanBeFavoritedDirectly() {
        AI_STUB.respondWith(200, TWO_PAPERS);
        String token = registerAndLogin("searcher");

        long paperId = search(token, Map.of("query", "对比学习"))
                .at("papers").get(0).get("id").asLong();

        ApiClient.Response favorite = api.post("/api/users/me/favorites/" + paperId, token);

        assertThat(favorite.status()).as(favorite.describe()).isEqualTo(200);
        assertThat(favorite.at("paper").get("title").asString()).isEqualTo("论文一");
    }

    @Test
    @DisplayName("重复检索复用同一行,不会重复入库")
    void repeatedSearchIsIdempotent() {
        AI_STUB.respondWith(200, TWO_PAPERS);
        String token = registerAndLogin("searcher");

        long first = search(token, Map.of("query", "对比学习")).at("papers").get(0).get("id").asLong();
        long second = search(token, Map.of("query", "对比学习")).at("papers").get(0).get("id").asLong();

        assertThat(second).isEqualTo(first);
        assertThat(paperCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("元数据不全的条目被跳过,其余照常返回")
    void skipsIncompletePapers() {
        AI_STUB.respondWith(200, """
                {"query": "q", "plan": {"keywords": "k", "rationale": "r"}, "sourceLabel": "Crossref",
                 "papers": [
                   {"source": "crossref", "externalId": "", "title": "缺外部 ID"},
                   {"source": "crossref", "externalId": "10.1/ok", "title": "完整的一篇"},
                   {"source": "", "externalId": "x", "title": "缺来源"}
                 ]}
                """);
        String token = registerAndLogin("searcher");

        ApiClient.Response response = search(token, Map.of("query", "检索"));

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(response.at("papers").size()).isEqualTo(1);
        assertThat(response.at("papers").get(0).get("title").asString()).isEqualTo("完整的一篇");
    }

    @Test
    @DisplayName("结果为空时正常返回空列表,而不是报错")
    void emptyResultIsNotAnError() {
        AI_STUB.respondWith(200, EMPTY_RESULT);
        String token = registerAndLogin("searcher");

        ApiClient.Response response = search(token, Map.of("query", "一个搜不到东西的词"));

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(response.at("papers").size()).isZero();
    }

    // ── 发给 ai-service 的请求 ────────────────────────────────

    @Test
    @DisplayName("请求体带上查询与条数")
    void forwardsQueryAndLimit() {
        AI_STUB.respondWith(200, EMPTY_RESULT);
        String token = registerAndLogin("searcher");

        search(token, Map.of("query", "扩散模型", "limit", 3));

        assertThat(AI_STUB.receivedBodies()).hasSize(1);
        assertThat(AI_STUB.receivedBodies().get(0))
                .contains("\"query\":\"扩散模型\"")
                .contains("\"limit\":3");
    }

    @Test
    @DisplayName("不传 limit 时用默认条数")
    void usesDefaultLimitWhenAbsent() {
        AI_STUB.respondWith(200, EMPTY_RESULT);
        String token = registerAndLogin("searcher");

        search(token, Map.of("query", "扩散模型"));

        assertThat(AI_STUB.receivedBodies().get(0)).contains("\"limit\":5");
    }

    // ── 参数校验 ──────────────────────────────────────────────

    @Test
    @DisplayName("查询为空或过短返回 400,并指出是哪个字段")
    void rejectsBlankOrTooShortQuery() {
        String token = registerAndLogin("searcher");

        for (String bad : new String[]{"", " ", "x"}) {
            ApiClient.Response response = search(token, Map.of("query", bad));
            assertThat(response.status()).as("query=%s → %s", bad, response.describe()).isEqualTo(400);
            assertThat(response.at("fieldErrors").get("query")).isNotNull();
        }
    }

    @Test
    @DisplayName("limit 越界返回 400")
    void rejectsOutOfRangeLimit() {
        String token = registerAndLogin("searcher");

        for (int bad : new int[]{0, -1, 21}) {
            ApiClient.Response response = search(token, Map.of("query", "扩散模型", "limit", bad));
            assertThat(response.status()).as("limit=%d → %s", bad, response.describe()).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("未登录不能检索")
    void requiresAuthentication() {
        AI_STUB.respondWith(200, EMPTY_RESULT);

        assertThat(api.postJson("/api/papers/search", json(Map.of("query", "扩散模型"))).status())
                .isEqualTo(401);

        String forged = forger.forge(9999L, "ghost",
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(600));
        assertThat(search(forged, Map.of("query", "扩散模型")).status()).isEqualTo(401);
    }

    // ── 上游故障 ──────────────────────────────────────────────

    @Test
    @DisplayName("ai-service 报错时返回 502,而不是 500")
    void mapsUpstreamErrorToBadGateway() {
        AI_STUB.respondWith(502, "{\"detail\":\"查询拆解失败:大模型超时\"}");
        String token = registerAndLogin("searcher");

        ApiClient.Response response = search(token, Map.of("query", "扩散模型"));

        assertThat(response.status()).as(response.describe()).isEqualTo(502);
    }

    @Test
    @DisplayName("ai-service 连不上时返回 503(暂时不可用,而不是我们的 bug)")
    void mapsConnectionFailureToServiceUnavailable() throws IOException {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();   // 占一个端口再立刻关掉,得到一个确定没人监听的端口
        }

        AiSearchClient client = new AiSearchClient("http://127.0.0.1:" + unusedPort);

        assertThatThrownBy(() -> client.search("扩散模型", 5))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("检索服务不可用");
    }

    private int paperCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM paper", Integer.class);
    }
}
