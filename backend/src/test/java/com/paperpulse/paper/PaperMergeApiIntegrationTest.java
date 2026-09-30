package com.paperpulse.paper;

import com.paperpulse.support.AbstractIntegrationTest;
import com.paperpulse.support.ApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 跨源合并:同一篇论文因为命中不同数据源而在库里存成两行的问题。
 *
 * <p>触发场景很具体:检索并发查三个源,arXiv 上的预印本与正式发表版
 * 来源与外部 ID 都不同、DOI 也不同(预印本的 DOI 与期刊 DOI 本就是两个),
 * 原有的两级查找认不出它们是同一篇。
 *
 * <p>这里走 {@code POST /api/papers} 这条路径 —— 检索路径最终也汇到 {@code PaperService.resolve}。
 */
class PaperMergeApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record Draft(String source, String externalId, String title, List<String> authors,
                         Integer year, String venue, String abstractText) {

        static Draft of(String source, String externalId, String title, List<String> authors, Integer year) {
            return new Draft(source, externalId, title, authors, year, null, null);
        }
    }

    private String registerAndLogin(String username) {
        registerOk(username, username + "@example.com", "secret123");
        return loginAndGetToken(username, "secret123");
    }

    private ApiClient.Response upsert(String token, Draft draft) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", draft.source());
        body.put("externalId", draft.externalId());
        body.put("title", draft.title());
        body.put("authors", draft.authors());
        if (draft.year() != null) {
            body.put("publicationYear", draft.year());
        }
        if (draft.venue() != null) {
            body.put("venue", draft.venue());
        }
        if (draft.abstractText() != null) {
            body.put("abstractText", draft.abstractText());
        }
        return api.postJson("/api/papers", token, json(body));
    }

    private long idOf(ApiClient.Response response) {
        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        return response.at("id").asLong();
    }

    private int paperCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM paper", Integer.class);
    }

    private int aliasCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM paper_alias", Integer.class);
    }

    // ── 合并 ──────────────────────────────────────────────────

    @Test
    @DisplayName("同一篇论文的预印本与正式版合并成一行")
    void preprintAndPublishedVersionBecomeOneRow() {
        String token = registerAndLogin("reader");
        List<String> authors = List.of("Wei Li", "Bo Wang");

        long fromArxiv = idOf(upsert(token, Draft.of(
                "arxiv", "2502.19271", "Contrastive Learning for Recommendation", authors, 2024)));
        long fromCrossref = idOf(upsert(token, Draft.of(
                "crossref", "10.1145/abcd.1234", "Contrastive Learning for Recommendation", authors, 2025)));

        assertThat(fromCrossref).as("同一篇的另一个版本应当并到同一行").isEqualTo(fromArxiv);
        assertThat(paperCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("换个顺序也一样合并")
    void mergeIsOrderIndependent() {
        String token = registerAndLogin("reader");
        List<String> authors = List.of("Wei Li");

        long fromCrossref = idOf(upsert(token, Draft.of(
                "crossref", "10.1145/abcd.1234", "A Survey", authors, 2024)));
        long fromArxiv = idOf(upsert(token, Draft.of(
                "arxiv", "2502.00001", "A Survey", authors, 2024)));

        assertThat(fromArxiv).isEqualTo(fromCrossref);
        assertThat(paperCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("版本后缀不影响合并")
    void versionSuffixStillMerges() {
        String token = registerAndLogin("reader");
        List<String> authors = List.of("Wei Li");

        long first = idOf(upsert(token, Draft.of(
                "arxiv", "2502.00001", "IGNiteR: News Recommendation (Extended Version)", authors, 2023)));
        long second = idOf(upsert(token, Draft.of(
                "crossref", "10.1145/abcd.1234", "IGNiteR: News Recommendation", authors, 2023)));

        assertThat(second).isEqualTo(first);
        assertThat(paperCount()).isEqualTo(1);
    }

    // ── 合并要留得住 ──────────────────────────────────────────

    @Test
    @DisplayName("合并后再遇到被合并掉的那个身份,仍命中同一行")
    void mergedIdentityKeepsResolvingToTheSameRow() {
        String token = registerAndLogin("reader");
        List<String> authors = List.of("Wei Li");

        long first = idOf(upsert(token, Draft.of("arxiv", "2502.00001", "A Survey", authors, 2024)));
        idOf(upsert(token, Draft.of("crossref", "10.1145/abcd.1234", "A Survey", authors, 2024)));
        // 别名就是为这一步存在的:少了它,这次会按 (arxiv, 2502.00001) 查不到行,又新建一个
        long again = idOf(upsert(token, Draft.of("arxiv", "2502.00001", "A Survey", authors, 2024)));

        assertThat(again).isEqualTo(first);
        assertThat(paperCount()).isEqualTo(1);
        assertThat(aliasCount()).as("被合并掉的身份应当留下一条别名").isEqualTo(1);
    }

    @Test
    @DisplayName("合并只补空缺,不覆盖已有值")
    void mergeFillsGapsWithoutOverwriting() {
        String token = registerAndLogin("reader");
        List<String> authors = List.of("Wei Li");

        // 正式版先到:有会议名,没有摘要
        upsert(token, new Draft("crossref", "10.1145/abcd.1234", "A Survey", authors, 2024,
                "NeurIPS", null));
        // 预印本后到:有摘要,没有会议名,标题还带后缀
        ApiClient.Response merged = upsert(token, new Draft("arxiv", "2502.00001",
                "A Survey (Extended Version)", authors, 2024, null, "来自 arXiv 的摘要"));

        assertThat(merged.text("abstractText")).as("预印本补上摘要").isEqualTo("来自 arXiv 的摘要");
        assertThat(merged.text("venue")).as("正式版的会议名不该被空值抹掉").isEqualTo("NeurIPS");
        assertThat(merged.text("title")).as("标题是变体不是更正,不该被带后缀的那个覆盖")
                .isEqualTo("A Survey");
    }

    // ── 不该合并的,一律不合并 ────────────────────────────────

    @Test
    @DisplayName("标题相同但作者不同 —— 不合并")
    void sameTitleDifferentAuthorsStaysSeparate() {
        String token = registerAndLogin("reader");

        long first = idOf(upsert(token, Draft.of(
                "crossref", "10.1145/a", "A Survey", List.of("Wei Li"), 2024)));
        long second = idOf(upsert(token, Draft.of(
                "arxiv", "2502.00001", "A Survey", List.of("Someone Else"), 2024)));

        // 实测 60 篇里就有 1 篇标题完全相同但作者不同 —— 只看标题会在这里合错
        assertThat(second).isNotEqualTo(first);
        assertThat(paperCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("标题、作者都相同但年份差太远 —— 不合并")
    void sameTitleAndAuthorsFarApartInYearsStaysSeparate() {
        String token = registerAndLogin("reader");
        List<String> authors = List.of("Wei Li");

        long first = idOf(upsert(token, Draft.of("crossref", "10.1145/a", "A Survey", authors, 2014)));
        long second = idOf(upsert(token, Draft.of("arxiv", "2502.00001", "A Survey", authors, 2024)));

        assertThat(second).isNotEqualTo(first);
        assertThat(paperCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("年份缺失 —— 不合并")
    void missingYearStaysSeparate() {
        String token = registerAndLogin("reader");
        List<String> authors = List.of("Wei Li");

        long first = idOf(upsert(token, Draft.of("crossref", "10.1145/a", "A Survey", authors, null)));
        long second = idOf(upsert(token, Draft.of("arxiv", "2502.00001", "A Survey", authors, 2024)));

        // 缺了年份,"同名同作者但不同时期"这条防线就没了 —— 宁可多一行
        assertThat(second).isNotEqualTo(first);
        assertThat(paperCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("作者缺失 —— 不合并")
    void missingAuthorsStaysSeparate() {
        String token = registerAndLogin("reader");

        long first = idOf(upsert(token, Draft.of("crossref", "10.1145/a", "A Survey", List.of(), 2024)));
        long second = idOf(upsert(token, Draft.of("arxiv", "2502.00001", "A Survey", List.of("Wei Li"), 2024)));

        assertThat(second).isNotEqualTo(first);
        assertThat(paperCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("不该合并时不会留下别名")
    void noAliasWhenNotMerged() {
        String token = registerAndLogin("reader");

        upsert(token, Draft.of("crossref", "10.1145/a", "A Survey", List.of("Wei Li"), 2024));
        upsert(token, Draft.of("arxiv", "2502.00001", "A Survey", List.of("Someone Else"), 2024));

        assertThat(aliasCount()).isZero();
    }

    // ── 既有行为不受影响 ──────────────────────────────────────

    @Test
    @DisplayName("同一身份重复提交仍然幂等,且不产生别名")
    void repeatedSameIdentityIsStillIdempotent() {
        String token = registerAndLogin("reader");
        Draft draft = Draft.of("crossref", "10.1145/a", "A Survey", List.of("Wei Li"), 2024);

        long first = idOf(upsert(token, draft));
        long second = idOf(upsert(token, draft));

        assertThat(second).isEqualTo(first);
        assertThat(paperCount()).isEqualTo(1);
        assertThat(aliasCount()).as("同一个身份走的是原有路径,不该记别名").isZero();
    }
}
