package com.paperpulse.recommend;

import com.paperpulse.support.AbstractIntegrationTest;
import com.paperpulse.support.ApiClient;
import com.paperpulse.support.StubAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 个性化推荐(REQ-004)。
 *
 * <p>ai-service 用真实的 HTTP 桩替代;候选的取回是真的,只有"候选从哪来"被换掉了。
 * 打分、排除已知、排探索位这些都在被测路径上。
 */
class RecommendationApiIntegrationTest extends AbstractIntegrationTest {

    private static final StubAiService AI_STUB = new StubAiService();

    @DynamicPropertySource
    static void pointAtStub(DynamicPropertyRegistry registry) {
        registry.add("app.ai-service.base-url", AI_STUB::getBaseUrl);
    }

    /** 两个兴趣标签的 key —— 必须真实存在于受控词表里。 */
    private static final String TOP_TAG = "recommender_system";
    private static final String OTHER_TAG = "information_retrieval";

    @BeforeEach
    void resetStub() {
        AI_STUB.reset();
    }

    private String registerAndLogin(String username) {
        registerOk(username, username + "@example.com", "secret123");
        return loginAndGetToken(username, "secret123");
    }

    private void setInterests(String token, String topTag, int topWeight, String otherTag, int otherWeight) {
        ApiClient.Response response = api.putJson("/api/users/me/interests", token,
                json(Map.of("interests", List.of(
                        Map.of("tag", topTag, "weight", topWeight),
                        Map.of("tag", otherTag, "weight", otherWeight)))));
        assertThat(response.status()).as(response.describe()).isEqualTo(200);
    }

    private static String candidate(String externalId, String title, String tag, Integer year) {
        return """
                {"source": "crossref", "externalId": "%s", "doi": "%s", "title": "%s",
                 "authors": ["Wei Li"], "publicationYear": %s, "matchedTags": ["%s"]}
                """.formatted(externalId, externalId, title, year, tag);
    }

    private static String candidates(String... papers) {
        return "{\"sourceLabel\": \"Crossref\", \"papers\": [" + String.join(",", papers) + "]}";
    }

    private ApiClient.Response recommend(String token, String query) {
        return api.get("/api/users/me/recommendations" + query, token);
    }

    // ── 空状态与鉴权 ──────────────────────────────────────────

    @Test
    @DisplayName("还没选兴趣标签时给出引导,而不是报错")
    void noInterestsYieldsHint() {
        String token = registerAndLogin("reader");

        ApiClient.Response response = recommend(token, "");

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(response.text("hint")).contains("兴趣标签");
        assertThat(response.at("recommendations").size()).isZero();
        assertThat(AI_STUB.candidates().receivedBodies()).as("没有兴趣就不该去麻烦上游").isEmpty();
    }

    @Test
    @DisplayName("未登录不能取推荐")
    void requiresAuthentication() {
        assertThat(api.get("/api/users/me/recommendations").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("上游不可用时返回 502,而不是 500")
    void upstreamFailureMapsToBadGateway() {
        String token = registerAndLogin("reader");
        setInterests(token, TOP_TAG, 5, OTHER_TAG, 1);
        AI_STUB.candidates().respondWith(503, "{\"detail\":\"所有检索路都失败了\"}");

        assertThat(recommend(token, "").status()).isEqualTo(502);
    }

    @Test
    @DisplayName("候选为空时返回空列表,不算错误")
    void emptyCandidatesIsNotAnError() {
        String token = registerAndLogin("reader");
        setInterests(token, TOP_TAG, 5, OTHER_TAG, 1);
        AI_STUB.candidates().respondWith(200, "{\"sourceLabel\":\"Crossref\",\"papers\":[]}");

        ApiClient.Response response = recommend(token, "");

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(response.at("recommendations").size()).isZero();
    }

    // ── 取候选 ────────────────────────────────────────────────

    @Test
    @DisplayName("按兴趣标签的检索词去取候选,一路一个标签")
    void requestsCandidatesPerInterestTag() {
        String token = registerAndLogin("reader");
        setInterests(token, TOP_TAG, 5, OTHER_TAG, 1);
        AI_STUB.candidates().respondWith(200, candidates(
                candidate("10.1/a", "甲", TOP_TAG, 2026)));

        recommend(token, "");

        assertThat(AI_STUB.candidates().receivedBodies()).hasSize(1);
        String sent = AI_STUB.candidates().receivedBodies().get(0);
        assertThat(sent).contains(TOP_TAG).contains(OTHER_TAG);
        // 标签自带检索词(InterestTag.s2Query),不该把标签 key 直接当检索词发出去
        assertThat(sent).contains("recommender system");
    }

    @Test
    @DisplayName("返回带分数的推荐,并附上推荐理由")
    void returnsScoredRecommendationsWithReason() {
        String token = registerAndLogin("reader");
        setInterests(token, TOP_TAG, 5, OTHER_TAG, 1);
        AI_STUB.candidates().respondWith(200, candidates(
                candidate("10.1/a", "推荐系统的论文", TOP_TAG, 2026)));

        ApiClient.Response response = recommend(token, "");
        JsonNode items = response.at("recommendations");

        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).get("score").asDouble()).isGreaterThan(0);
        assertThat(items.get(0).get("reason").asString())
                .contains("推荐系统")   // 展示名,不是标签 key
                .contains("5");        // 权重
        assertThat(items.get(0).get("paper").get("id").asLong()).isPositive();
    }

    @Test
    @DisplayName("权重高的兴趣排前面 —— 这是个性化的核心")
    void higherWeightInterestRanksFirst() {
        String token = registerAndLogin("reader");
        setInterests(token, TOP_TAG, 5, OTHER_TAG, 1);
        AI_STUB.candidates().respondWith(200, candidates(
                // 低权重兴趣的候选更新,高权重兴趣的候选更旧 —— 兴趣权重仍应当胜出
                candidate("10.1/low", "来自低权重兴趣", OTHER_TAG, 2026),
                candidate("10.1/high", "来自高权重兴趣", TOP_TAG, 2022)));

        ApiClient.Response response = recommend(token, "");

        assertThat(response.at("recommendations").get(0).get("paper").get("title").asString())
                .isEqualTo("来自高权重兴趣");
    }

    // ── 排除已知 ──────────────────────────────────────────────

    @Test
    @DisplayName("已收藏的论文不再推荐 —— 推荐的意义是给你没看过的")
    void alreadyFavoritedPapersAreExcluded() {
        String token = registerAndLogin("reader");
        setInterests(token, TOP_TAG, 5, OTHER_TAG, 1);
        AI_STUB.candidates().respondWith(200, candidates(
                candidate("10.1/a", "已经收藏过的", TOP_TAG, 2026),
                candidate("10.1/b", "还没看过的", TOP_TAG, 2026)));

        // 先搜出本地 id 再收藏
        long knownId = api.postJson("/api/papers", token, json(Map.of(
                "source", "crossref", "externalId", "10.1/a", "doi", "10.1/a",
                "title", "已经收藏过的", "authors", List.of("Wei Li"),
                "publicationYear", 2026))).at("id").asLong();
        assertThat(api.post("/api/users/me/favorites/" + knownId, token).status()).isEqualTo(200);

        ApiClient.Response response = recommend(token, "");

        assertThat(response.at("recommendations").size()).isEqualTo(1);
        assertThat(response.at("recommendations").get(0).get("paper").get("title").asString())
                .isEqualTo("还没看过的");
    }

    @Test
    @DisplayName("读过的论文同样排除")
    void alreadyReadPapersAreExcluded() {
        String token = registerAndLogin("reader");
        setInterests(token, TOP_TAG, 5, OTHER_TAG, 1);
        AI_STUB.candidates().respondWith(200, candidates(
                candidate("10.1/a", "读过的", TOP_TAG, 2026)));

        long knownId = api.postJson("/api/papers", token, json(Map.of(
                "source", "crossref", "externalId", "10.1/a", "doi", "10.1/a",
                "title", "读过的", "authors", List.of("Wei Li"),
                "publicationYear", 2026))).at("id").asLong();
        api.post("/api/users/me/history/" + knownId, token);

        assertThat(recommend(token, "").at("recommendations").size()).isZero();
    }

    // ── 探索位 ────────────────────────────────────────────────

    @Test
    @DisplayName("探索位:比例调大,非主要方向的论文就多")
    void diversityRatioControlsExplorationSlots() {
        String token = registerAndLogin("reader");
        setInterests(token, TOP_TAG, 5, OTHER_TAG, 1);
        AI_STUB.candidates().respondWith(200, candidates(
                candidate("10.1/a1", "主方向 1", TOP_TAG, 2026),
                candidate("10.1/a2", "主方向 2", TOP_TAG, 2026),
                candidate("10.1/a3", "主方向 3", TOP_TAG, 2026),
                candidate("10.1/a4", "主方向 4", TOP_TAG, 2026),
                candidate("10.1/b1", "其他方向 1", OTHER_TAG, 2026),
                candidate("10.1/b2", "其他方向 2", OTHER_TAG, 2026)));

        ApiClient.Response none = recommend(token, "?limit=4&diversityRatio=0");
        assertThat(titles(none)).as("比例为 0 时全是主方向").containsExactly(
                "主方向 1", "主方向 2", "主方向 3", "主方向 4");

        ApiClient.Response half = recommend(token, "?limit=4&diversityRatio=0.5");
        assertThat(titles(half)).as("比例 0.5 时留两个位置给其他方向").containsExactly(
                "主方向 1", "主方向 2", "其他方向 1", "其他方向 2");
    }

    @Test
    @DisplayName("最终列表按分数排序 —— 探索位决定谁能进榜,不决定顺序")
    void listStaysSortedByScoreEvenWithExploration() {
        String token = registerAndLogin("reader");
        // 低权重兴趣的候选更新,分数反而更高 —— 它就该排在前面
        setInterests(token, TOP_TAG, 5, OTHER_TAG, 4);
        int thisYear = java.time.Year.now().getValue();
        AI_STUB.candidates().respondWith(200, candidates(
                candidate("10.1/old", "较旧的主要方向", TOP_TAG, thisYear - 10),
                candidate("10.1/new", "更新的次要方向", OTHER_TAG, thisYear)));

        ApiClient.Response response = recommend(token, "?limit=2&diversityRatio=0.5");
        JsonNode items = response.at("recommendations");

        assertThat(items.get(0).get("score").asDouble())
                .as("列表必须是按分数降序的,否则看起来根本没排序")
                .isGreaterThanOrEqualTo(items.get(1).get("score").asDouble());
        assertThat(titles(response)).containsExactly("更新的次要方向", "较旧的主要方向");
    }

    @Test
    @DisplayName("主方向候选不够时,探索位由其他方向补满,不返回不满一页")
    void explorationPoolFillsUpWhenExploitationIsShort() {
        String token = registerAndLogin("reader");
        setInterests(token, TOP_TAG, 5, OTHER_TAG, 1);
        AI_STUB.candidates().respondWith(200, candidates(
                candidate("10.1/a1", "主方向 1", TOP_TAG, 2026),
                candidate("10.1/b1", "其他方向 1", OTHER_TAG, 2026),
                candidate("10.1/b2", "其他方向 2", OTHER_TAG, 2026)));

        ApiClient.Response response = recommend(token, "?limit=4&diversityRatio=0.3");

        assertThat(response.at("recommendations").size()).as("有多少给多少,不报错").isEqualTo(3);
    }

    @Test
    @DisplayName("回显生效的探索位比例,便于调参时确认真的生效")
    void echoesEffectiveDiversityRatio() {
        String token = registerAndLogin("reader");
        setInterests(token, TOP_TAG, 5, OTHER_TAG, 1);
        AI_STUB.candidates().respondWith(200, candidates(
                candidate("10.1/a", "甲", TOP_TAG, 2026)));

        assertThat(recommend(token, "?diversityRatio=0.5").at("diversityRatio").asDouble())
                .isEqualTo(0.5);
        // 越界的值回落到默认,而不是照单全收
        assertThat(recommend(token, "?diversityRatio=9").at("diversityRatio").asDouble())
                .isEqualTo(0.3);
    }

    private static List<String> titles(ApiClient.Response response) {
        return response.at("recommendations").valueStream()
                .map(item -> item.get("paper").get("title").asString())
                .toList();
    }
}
