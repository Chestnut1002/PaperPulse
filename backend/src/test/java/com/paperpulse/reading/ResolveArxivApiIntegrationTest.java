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
 * 按标题反查 arXiv 预印本(REQ-003 P3)。
 *
 * <p>救的是**没有 arXiv 编号的论文**(尤其 Crossref 那批,实测 0/11)—— 它们此前连精读入口都没有。
 *
 * <p>认不认由 {@code PaperMatcher} 决定,所以这里的重点用例大多落在"像但不是"那一侧:
 * **认错会把用户领到另一篇论文的全文上**,比认不出来糟得多。
 */
class ResolveArxivApiIntegrationTest extends AbstractIntegrationTest {

    private static final StubAiService AI_STUB = new StubAiService();

    @DynamicPropertySource
    static void pointAtStub(DynamicPropertyRegistry registry) {
        registry.add("app.ai-service.base-url", AI_STUB::getBaseUrl);
    }

    /** 库里那篇期刊论文的标题/作者/年份 —— 候选要跟它对上才算"同一篇"。 */
    private static final String TITLE = "一篇期刊论文";
    private static final List<String> AUTHORS = List.of("Wei Li");
    private static final int YEAR = 2020;

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

    /** 往库里放一篇期刊论文(没有 arXiv 编号)—— 正是反查要救的那一类。 */
    private long storeJournalPaper(String token, String title, List<String> authors, Integer year) {
        return api.postJson("/api/papers", token, json(Map.of(
                        "source", "crossref", "externalId", "10.1/x", "title", title,
                        "authors", authors, "publicationYear", year)))
                .at("id").asLong();
    }

    /**
     * 库里那种"什么都没有"的记录:无作者、无年份。
     *
     * <p>Map.of 不收 null,所以这里手工拼一个 —— 而 null 正是这个用例要测的东西。
     */
    private long storeBarePaper(String token, String title) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("source", "crossref");
        body.put("externalId", "10.1/bare");
        body.put("title", title);
        body.put("authors", List.of());
        body.put("publicationYear", null);
        return api.postJson("/api/papers", token, json(body)).at("id").asLong();
    }

    private ApiClient.Response resolve(String token, long paperId) {
        return api.post("/api/papers/" + paperId + "/arxiv-lookup", token);
    }

    /** 单条候选。arXiv 返回的形状由 Python 侧映射,这里只摆出 Java 会读的字段。 */
    private static String candidate(String title, List<String> authors, Integer year, String arxivId) {
        return """
                {"source": "arxiv", "externalId": "%s", "arxivId": %s,
                 "doi": null, "title": "%s", "authors": [%s],
                 "abstractText": "摘要", "publicationYear": %s,
                 "venue": null, "url": "https://arxiv.org/abs/%s"}
                """.formatted(arxivId, arxivId == null ? "null" : "\"" + arxivId + "\"", title,
                authors.stream().map(name -> "\"" + name + "\"").reduce((a, b) -> a + ", " + b).orElse(""),
                year == null ? "null" : year.toString(), arxivId);
    }

    private static String feed(String... candidates) {
        return "{\"candidates\": [" + String.join(",", candidates) + "]}";
    }

    private String arxivIdInDb(long paperId) {
        return jdbcTemplate.queryForObject(
                "SELECT arxiv_id FROM paper WHERE id = ?", String.class, paperId);
    }

    @Test
    @DisplayName("按标题找到预印本,编号落库 —— 这篇从此能精读")
    void resolvesAndStoresArxivId() {
        String token = registerAndLogin("reader");
        long paperId = storeJournalPaper(token, TITLE, AUTHORS, YEAR);
        AI_STUB.titleLookup().respondWith(200, feed(candidate(TITLE, AUTHORS, YEAR, "2002.02126")));

        ApiClient.Response response = resolve(token, paperId);

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(response.text("arxivId")).isEqualTo("2002.02126");
        assertThat(response.at("id").asLong()).as("返回的仍是同一篇,不是新建的一行").isEqualTo(paperId);
        assertThat(arxivIdInDb(paperId)).as("编号要写进库里 —— 所有用户受益").isEqualTo("2002.02126");
    }

    @Test
    @DisplayName("反查用的是库里的标题,不是别的东西")
    void sendsTheStoredTitleUpstream() {
        String token = registerAndLogin("reader");
        long paperId = storeJournalPaper(token, TITLE, AUTHORS, YEAR);
        AI_STUB.titleLookup().respondWith(200, feed(candidate(TITLE, AUTHORS, YEAR, "2002.02126")));

        resolve(token, paperId);

        assertThat(AI_STUB.titleLookup().receivedBodies().get(0)).contains(TITLE);
    }

    @Test
    @DisplayName("已有编号时直接返回,不打上游")
    void skipsUpstreamWhenArxivIdAlreadyKnown() {
        String token = registerAndLogin("reader");
        long paperId = api.postJson("/api/papers", token, json(Map.of(
                        "source", "semantic_scholar", "externalId", "abc123", "title", TITLE,
                        "authors", AUTHORS, "publicationYear", YEAR, "arxivId", "2002.02126")))
                .at("id").asLong();

        ApiClient.Response response = resolve(token, paperId);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text("arxivId")).isEqualTo("2002.02126");
        assertThat(AI_STUB.titleLookup().receivedBodies())
                .as("已经知道答案就别再查一次 —— 这个接口可能被反复点").isEmpty();
    }

    @Test
    @DisplayName("标题对得上但作者不同:不认,也不落库")
    void rejectsCandidateWithDifferentAuthors() {
        String token = registerAndLogin("reader");
        long paperId = storeJournalPaper(token, TITLE, AUTHORS, YEAR);
        // 同名不同作者是真实存在的 —— 认错会把用户领到另一篇论文上
        AI_STUB.titleLookup().respondWith(200,
                feed(candidate(TITLE, List.of("Someone Else"), YEAR, "2002.02126")));

        ApiClient.Response response = resolve(token, paperId);

        assertThat(response.status()).isEqualTo(404);
        assertThat(arxivIdInDb(paperId)).as("没认准就不能留下痕迹").isNull();
    }

    @Test
    @DisplayName("标题作者都对但年份差太远:不认")
    void rejectsCandidateFromFarDifferentYear() {
        String token = registerAndLogin("reader");
        long paperId = storeJournalPaper(token, TITLE, AUTHORS, YEAR);
        AI_STUB.titleLookup().respondWith(200,
                feed(candidate(TITLE, AUTHORS, YEAR - 10, "2002.02126")));

        ApiClient.Response response = resolve(token, paperId);

        assertThat(response.status()).isEqualTo(404);
        assertThat(arxivIdInDb(paperId)).isNull();
    }

    @Test
    @DisplayName("候选里的近似项要跳过,取真正对上的那一条")
    void picksTheMatchingCandidateAmongSeveral() {
        String token = registerAndLogin("reader");
        long paperId = storeJournalPaper(token, TITLE, AUTHORS, YEAR);
        // arXiv 的标题检索会带回"名字长得像"的论文(实测 "Not All Attention Is All You Need")
        AI_STUB.titleLookup().respondWith(200, feed(
                candidate("另一篇毫不相干的论文", List.of("Other Author"), 2021, "2104.04692"),
                candidate(TITLE, AUTHORS, YEAR, "2002.02126")));

        ApiClient.Response response = resolve(token, paperId);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text("arxivId")).as("必须是第二条,不能是排在前面的近似项")
                .isEqualTo("2002.02126");
    }

    @Test
    @DisplayName("候选对得上但没有编号:跳过,当作没找到")
    void skipsCandidateWithoutArxivId() {
        String token = registerAndLogin("reader");
        long paperId = storeJournalPaper(token, TITLE, AUTHORS, YEAR);
        AI_STUB.titleLookup().respondWith(200, feed(candidate(TITLE, AUTHORS, YEAR, null)));

        ApiClient.Response response = resolve(token, paperId);

        assertThat(response.status()).as("没有编号的候选对'能不能精读'毫无帮助").isEqualTo(404);
        assertThat(arxivIdInDb(paperId)).isNull();
    }

    @Test
    @DisplayName("库里无作者无年份时,标题一字不差也认 —— 否则这篇永远读不了")
    void acceptsTitleOnlyWhenOurRecordHasNothingElse() {
        String token = registerAndLogin("reader");
        // 实测(2026-10-01):Crossref 有这种记录,库里 166 篇里有 8 篇;
        // 例如 "Polymer-Agent: …",arXiv 上有同名的 2601.16376,标准规则却认不出来
        long paperId = storeBarePaper(token, "Polymer-Agent: Large Language Model Agent for Polymer Design");
        AI_STUB.titleLookup().respondWith(200, feed(candidate(
                "Polymer-Agent: Large Language Model Agent for Polymer Design",
                List.of("Vani Nigam", "Achuth Chandrasekhar"), 2026, "2601.16376")));

        ApiClient.Response response = resolve(token, paperId);

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(arxivIdInDb(paperId)).isEqualTo("2601.16376");
    }

    @Test
    @DisplayName("但我们这边有年份时就不再退让,该不认还是不认")
    void doesNotFallBackWhenOurRecordHasAYear() {
        String token = registerAndLogin("reader");
        long paperId = storeJournalPaper(token, TITLE, List.of(), YEAR);
        AI_STUB.titleLookup().respondWith(200,
                feed(candidate(TITLE, List.of("Someone Else"), YEAR, "2002.02126")));

        assertThat(resolve(token, paperId).status()).as("有佐证就该用佐证").isEqualTo(404);
        assertThat(arxivIdInDb(paperId)).isNull();
    }

    @Test
    @DisplayName("标题太短时,只凭标题不足以认定")
    void shortTitleIsNotEnoughOnItsOwn() {
        String token = registerAndLogin("reader");
        long paperId = storeBarePaper(token, "Editorial Note");
        AI_STUB.titleLookup().respondWith(200,
                feed(candidate("Editorial Note", List.of("Wei Li"), 2020, "2002.02126")));

        assertThat(resolve(token, paperId).status()).isEqualTo(404);
        assertThat(arxivIdInDb(paperId)).isNull();
    }

    @Test
    @DisplayName("arXiv 上没有就说没找到,而不是报错")
    void noCandidatesMeansNotFound() {
        String token = registerAndLogin("reader");
        long paperId = storeJournalPaper(token, TITLE, AUTHORS, YEAR);
        AI_STUB.titleLookup().respondWith(200, feed());

        ApiClient.Response response = resolve(token, paperId);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.text("message")).contains("没有找到");
    }

    @Test
    @DisplayName("上游不可用时返回 502")
    void upstreamFailureIsMapped() {
        String token = registerAndLogin("reader");
        long paperId = storeJournalPaper(token, TITLE, AUTHORS, YEAR);
        AI_STUB.titleLookup().respondWith(503, "{\"detail\":\"取 arXiv 元数据失败\"}");

        assertThat(resolve(token, paperId).status()).isEqualTo(502);
    }

    @Test
    @DisplayName("论文不存在返回 404")
    void unknownPaperIsNotFound() {
        String token = registerAndLogin("reader");

        assertThat(resolve(token, 999999L).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("未登录不能用")
    void requiresAuthentication() {
        assertThat(api.post("/api/papers/1/arxiv-lookup", null).status()).isEqualTo(401);
    }
}
