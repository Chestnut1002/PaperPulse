package com.paperpulse.reading;

import com.paperpulse.support.AbstractIntegrationTest;
import com.paperpulse.support.ApiClient;
import com.paperpulse.support.StubAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 精读问答(REQ-003)。
 *
 * <p>ai-service 用真实的 HTTP 桩替代 —— 抓全文、切节、调模型都在那边,
 * 这里要验的是**本服务的职责**:确认这篇能不能读、把请求转发出去、把上游的失败翻译对。
 */
class ReadingApiIntegrationTest extends AbstractIntegrationTest {

    private static final StubAiService AI_STUB = new StubAiService();

    @DynamicPropertySource
    static void pointAtStub(DynamicPropertyRegistry registry) {
        registry.add("app.ai-service.base-url", AI_STUB::getBaseUrl);
    }

    private static final String ANSWER = """
            {"answer": "这篇论文提出了一个多视图方法[[§1]]。\\n\\n具体而言……[[§3]]",
             "citations": [
               {"index": 1, "title": "1 Introduction", "excerpt": "论文正文的开头……"},
               {"index": 3, "title": "2 Method", "excerpt": "方法部分的开头……"}
             ],
             "omittedTurns": 0}
            """;

    @BeforeEach
    void resetStub() {
        AI_STUB.reset();
    }

    private String registerAndLogin(String username) {
        registerOk(username, username + "@example.com", "secret123");
        return loginAndGetToken(username, "secret123");
    }

    /** 建一篇论文,返回本地 id。`arxivId` 传 null 就得到一篇"读不了"的论文。 */
    private long paper(String token, String externalId, String arxivId) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("source", "arxiv");
        body.put("externalId", externalId);
        body.put("title", "一篇论文");
        body.put("authors", List.of("Wei Li"));
        body.put("publicationYear", 2024);
        if (arxivId != null) {
            body.put("arxivId", arxivId);
        }
        ApiClient.Response response = api.postJson("/api/papers", token, json(body));
        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        return response.at("id").asLong();
    }

    private ApiClient.Response ask(String token, long paperId, String question) {
        return ask(token, paperId, Map.of("question", question));
    }

    private ApiClient.Response ask(String token, long paperId, Map<String, Object> body) {
        return api.postJson("/api/papers/" + paperId + "/qa", token, json(body));
    }

    // ── 能不能读 ──────────────────────────────────────────────

    @Test
    @DisplayName("没有 arXiv 编号的论文读不了,并说清是为什么")
    void papersWithoutFullTextAreRejectedClearly() {
        String token = registerAndLogin("reader");
        long paperId = paper(token, "10.1145/abc", null);

        ApiClient.Response response = ask(token, paperId, "这篇讲了什么?");

        assertThat(response.status()).as(response.describe()).isEqualTo(400);
        assertThat(response.text("message"))
                .as("要说清是这一篇读不了,而不是服务坏了")
                .contains("没有可精读的全文");
        assertThat(AI_STUB.qa().receivedBodies()).as("读不了就不该去麻烦上游").isEmpty();
    }

    @Test
    @DisplayName("论文不存在时返回 404")
    void unknownPaperIsNotFound() {
        String token = registerAndLogin("reader");

        assertThat(ask(token, 999_999L, "问题?").status()).isEqualTo(404);
    }

    @Test
    @DisplayName("未登录不能提问")
    void requiresAuthentication() {
        assertThat(api.postJson("/api/papers/1/qa", json(Map.of("question", "问题?"))).status())
                .isEqualTo(401);
    }

    // ── 正常路径 ──────────────────────────────────────────────

    @Test
    @DisplayName("返回回答与引用")
    void returnsAnswerWithCitations() {
        String token = registerAndLogin("reader");
        long paperId = paper(token, "2502.19271", "2502.19271");
        AI_STUB.qa().respondWith(200, ANSWER);

        ApiClient.Response response = ask(token, paperId, "这篇论文提出了什么方法?");

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(response.text("answer")).contains("多视图方法").contains("[[§1]]");

        JsonNode citations = response.at("citations");
        assertThat(citations.size()).isEqualTo(2);
        assertThat(citations.get(0).get("title").asString()).isEqualTo("1 Introduction");
        assertThat(citations.get(0).get("excerpt").asString()).as("摘录来自原文").isNotEmpty();
    }

    @Test
    @DisplayName("把 arXiv 编号与对话历史一起发给上游")
    void forwardsArxivIdAndHistory() {
        String token = registerAndLogin("reader");
        long paperId = paper(token, "2502.19271", "2502.19271");
        AI_STUB.qa().respondWith(200, ANSWER);

        ask(token, paperId, Map.of(
                "question", "那它的实验呢?",
                "history", List.of(
                        Map.of("role", "user", "content", "这篇提出了什么方法?"),
                        Map.of("role", "assistant", "content", "提出了一个多视图方法[[§1]]"))));

        assertThat(AI_STUB.qa().receivedBodies()).hasSize(1);
        String sent = AI_STUB.qa().receivedBodies().get(0);
        assertThat(sent).contains("2502.19271").contains("那它的实验呢?").contains("提出了一个多视图方法");
    }

    @Test
    @DisplayName("省略的轮次数照实传回来")
    void reportsOmittedTurns() {
        String token = registerAndLogin("reader");
        long paperId = paper(token, "2502.19271", "2502.19271");
        AI_STUB.qa().respondWith(200, """
                {"answer": "回答", "citations": [], "omittedTurns": 3}
                """);

        ApiClient.Response response = ask(token, paperId, "问题?");

        // 静默截断会让用户以为模型没看到的问题它没看到 —— 必须让他知道
        assertThat(response.at("omittedTurns").asInt()).isEqualTo(3);
    }

    // ── 参数校验 ──────────────────────────────────────────────

    @Test
    @DisplayName("空问题或过短的问题返回 400")
    void rejectsBlankQuestion() {
        String token = registerAndLogin("reader");
        long paperId = paper(token, "2502.19271", "2502.19271");

        for (String bad : new String[]{"", " ", "x"}) {
            ApiClient.Response response = ask(token, paperId, bad);
            assertThat(response.status()).as("question=%s", bad).isEqualTo(400);
            assertThat(response.at("fieldErrors").get("question")).isNotNull();
        }
    }

    // ── 上游故障 ──────────────────────────────────────────────

    @Test
    @DisplayName("上游说这篇取不到全文时,映射成 400 而不是 404")
    void upstreamMissingFullTextIsABadRequest() {
        String token = registerAndLogin("reader");
        long paperId = paper(token, "2502.19271", "2502.19271");
        AI_STUB.qa().respondWith(404, "{\"detail\":\"arXiv 2502.19271 没有可用的 HTML 全文\"}");

        ApiClient.Response response = ask(token, paperId, "问题?");

        assertThat(response.status()).as(response.describe()).isEqualTo(400);
        assertThat(response.text("message")).contains("没有可用的 HTML 全文");
    }

    @Test
    @DisplayName("论文过长时映射成 400,并带上上游的解释")
    void upstreamTooLongIsABadRequest() {
        String token = registerAndLogin("reader");
        long paperId = paper(token, "2502.19271", "2502.19271");
        AI_STUB.qa().respondWith(422, "{\"detail\":\"这篇论文的正文约 200 千字符,已经超出上下文预算\"}");

        ApiClient.Response response = ask(token, paperId, "问题?");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("message")).contains("超出上下文预算");
    }

    @Test
    @DisplayName("上游连不上时返回 503")
    void upstreamDownIsServiceUnavailable() throws IOException {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        AiReadingClient client = new AiReadingClient(
                new com.paperpulse.config.AiServiceConfig().aiServiceRestClient(
                        "http://127.0.0.1:" + unusedPort),
                objectMapper);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                client.ask("2502.19271", "问题", List.of())))
                .isInstanceOf(com.paperpulse.common.ApiException.class)
                .hasMessageContaining("精读服务不可用");
    }
}
