package com.paperpulse.paper;

import com.paperpulse.support.AbstractIntegrationTest;
import com.paperpulse.support.ApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 论文的跨源身份(DOI)。
 *
 * <p>起因:检索会在 Semantic Scholar 与 Crossref 之间降级,同一个查询昨天走 A、今天走 B。
 * 唯一约束原先只有 {@code (source, external_id)},于是同一篇论文在两个来源下各存一行,
 * 用户的收藏 / 评分 / 阅读历史跟着分裂到两行上。
 *
 * <p>这里走的是 {@code POST /api/papers} 这条路径 —— 检索路径另有
 * {@code SearchApiIntegrationTest} 覆盖,两者最终都汇到 {@code PaperService.resolve}。
 */
class DoiApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String registerAndLogin(String username) {
        registerOk(username, username + "@example.com", "secret123");
        return loginAndGetToken(username, "secret123");
    }

    private ApiClient.Response upsert(String token, PaperDraft draft) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", draft.source());
        body.put("externalId", draft.externalId());
        body.put("title", draft.title());
        if (draft.doi() != null) {
            body.put("doi", draft.doi());
        }
        if (draft.venue() != null) {
            body.put("venue", draft.venue());
        }
        if (draft.abstractText() != null) {
            body.put("abstractText", draft.abstractText());
        }
        return api.postJson("/api/papers", token, json(body));
    }

    private record PaperDraft(String source, String externalId, String doi, String title,
                              String venue, String abstractText) {

        static PaperDraft of(String source, String externalId, String doi, String title) {
            return new PaperDraft(source, externalId, doi, title, null, null);
        }
    }

    private long idOf(ApiClient.Response response) {
        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        return response.at("id").asLong();
    }

    private int paperCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM paper", Integer.class);
    }

    // ── 跨源合并 ──────────────────────────────────────────────

    @Test
    @DisplayName("同一篇论文从两个来源进来只存一行")
    void sameDoiFromTwoSourcesIsOneRow() {
        String token = registerAndLogin("reader");

        long fromCrossref = idOf(upsert(token, PaperDraft.of(
                "crossref", "10.1000/abc", "10.1000/abc", "跨源论文")));
        long fromScholar = idOf(upsert(token, PaperDraft.of(
                "semantic_scholar", "S2-9f8e7d", "10.1000/abc", "跨源论文")));

        assertThat(fromScholar).as("同一个 DOI 必须收敛到同一行").isEqualTo(fromCrossref);
        assertThat(paperCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("两个来源的元数据互补到同一行,而不是互相覆盖")
    void metadataFromBothSourcesIsMerged() {
        String token = registerAndLogin("reader");

        // Crossref 有会议名没摘要
        upsert(token, new PaperDraft("crossref", "10.1000/abc", "10.1000/abc",
                "跨源论文", "NeurIPS", null));
        // S2 有摘要没会议名
        ApiClient.Response merged = upsert(token, new PaperDraft("semantic_scholar", "S2-9f8e7d",
                "10.1000/abc", "跨源论文", null, "这是从 S2 拿到的摘要"));

        assertThat(merged.text("venue")).as("先到的会议名不该被后来的空值抹掉").isEqualTo("NeurIPS");
        assertThat(merged.text("abstractText")).isEqualTo("这是从 S2 拿到的摘要");
        assertThat(paperCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("DOI 写法不同也认得出是同一篇")
    void doiFormsAreNormalized() {
        String token = registerAndLogin("reader");

        long first = idOf(upsert(token, PaperDraft.of(
                "crossref", "10.1000/abc", "10.1000/abc", "论文")));
        // 大写、带前缀 —— DOI 本身大小写不敏感,不归一就会认成两篇
        long second = idOf(upsert(token, PaperDraft.of(
                "semantic_scholar", "S2-x", "https://doi.org/10.1000/ABC", "论文")));

        assertThat(second).isEqualTo(first);
        assertThat(paperCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("响应里带上规范化之后的 DOI")
    void responseExposesNormalizedDoi() {
        String token = registerAndLogin("reader");

        ApiClient.Response response = upsert(token, PaperDraft.of(
                "semantic_scholar", "S2-x", "https://doi.org/10.1000/ABC", "论文"));

        assertThat(response.text("doi")).isEqualTo("10.1000/abc");
    }

    // ── 没有 DOI 时的行为不变 ─────────────────────────────────

    @Test
    @DisplayName("没有 DOI 仍按 (来源, 外部 ID) 幂等")
    void withoutDoiStillDedupesBySourceAndExternalId() {
        String token = registerAndLogin("reader");

        long first = idOf(upsert(token, PaperDraft.of("semantic_scholar", "S2-1", null, "论文")));
        long second = idOf(upsert(token, PaperDraft.of("semantic_scholar", "S2-1", null, "论文")));

        assertThat(second).isEqualTo(first);
        assertThat(paperCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("没有 DOI 时,不同来源的同名论文认不出来 —— 已知边界,不是回归")
    void withoutDoiCrossSourcePapersStaySeparate() {
        String token = registerAndLogin("reader");

        long first = idOf(upsert(token, PaperDraft.of("semantic_scholar", "S2-1", null, "同一篇论文")));
        long second = idOf(upsert(token, PaperDraft.of("crossref", "10.1000/abc", null, "同一篇论文")));

        // 没有 DOI 就没有跨源的依据:标题相同也不足以断定是同一篇(同名论文真实存在)。
        // 宁可多存一行,也不要错误地合并两篇不同的论文。
        assertThat(second).isNotEqualTo(first);
        assertThat(paperCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("形状不对的 DOI 当作没有,不拿它去认亲")
    void malformedDoiIsIgnored() {
        String token = registerAndLogin("reader");

        long first = idOf(upsert(token, PaperDraft.of("semantic_scholar", "S2-1", null, "甲")));
        ApiClient.Response second = upsert(token,
                PaperDraft.of("semantic_scholar", "S2-2", "not-a-doi", "乙"));

        assertThat(second.text("doi")).as("认不出来的值不该原样写进库").isNull();
        assertThat(idOf(second)).isNotEqualTo(first);
    }

    @Test
    @DisplayName("已有行后来才拿到 DOI 时补上,之后就能跨源认人")
    void doiIsBackfilledOnLaterSubmission() {
        String token = registerAndLogin("reader");

        long first = idOf(upsert(token, PaperDraft.of("semantic_scholar", "S2-1", null, "论文")));
        // 同一来源同一外部 ID 再提交一次,这次带上了 DOI
        ApiClient.Response withDoi = upsert(token,
                PaperDraft.of("semantic_scholar", "S2-1", "10.1000/abc", "论文"));

        assertThat(idOf(withDoi)).isEqualTo(first);
        assertThat(withDoi.text("doi")).isEqualTo("10.1000/abc");
        assertThat(paperCount()).isEqualTo(1);

        // 补齐之后再从别的来源进来,就认得出是同一篇了
        long fromCrossref = idOf(upsert(token,
                PaperDraft.of("crossref", "10.1000/abc", "10.1000/abc", "论文")));
        assertThat(fromCrossref).isEqualTo(first);
        assertThat(paperCount()).isEqualTo(1);
    }
}
