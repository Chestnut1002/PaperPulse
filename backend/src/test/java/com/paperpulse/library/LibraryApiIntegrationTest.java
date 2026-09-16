package com.paperpulse.library;

import com.paperpulse.paper.PaperService;
import com.paperpulse.paper.dto.PaperInput;
import com.paperpulse.support.AbstractIntegrationTest;
import com.paperpulse.support.ApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F6:收藏 / 阅读历史 / 论文评分。
 *
 * <p>重点覆盖三类容易出问题的地方:
 * <ul>
 *   <li><b>论文去重</b> —— 同一篇被多个人收藏只能有一行,否则星标数、推荐共现全都算错</li>
 *   <li><b>状态 vs 事件</b> —— 收藏/评分是覆盖,阅读是累加;写反了不会报错,只会慢慢攒出脏数据</li>
 *   <li><b>幂等与"没这回事"的分界</b> —— 重复收藏该成功,取消没收藏过的不该假装成功</li>
 * </ul>
 */
@DisplayName("F6 收藏/历史/评分")
class LibraryApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "secret123";
    private static final String PAPERS = "/api/papers";
    private static final String FAVORITES = "/api/users/me/favorites";
    private static final String HISTORY = "/api/users/me/history";
    private static final String RATINGS = "/api/users/me/ratings";

    /** 并发用例直接调服务,不走 HTTP —— 要压的是"查不到就插入"这个窗口,不是 Web 层。 */
    @Autowired
    private PaperService paperService;

    // ── 小工具 ──────────────────────────────────────────────

    /** 构造一份完整的论文元数据。用 LinkedHashMap 而不是 Map.of:用例需要删掉其中几个字段。 */
    private static Map<String, Object> paper(String externalId, String title) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "semantic_scholar");
        body.put("externalId", externalId);
        body.put("title", title);
        body.put("authors", List.of("Alice Zhang", "Bob Li"));
        body.put("abstractText", "这是 " + externalId + " 的摘要。");
        body.put("publicationYear", 2024);
        body.put("venue", "NeurIPS");
        body.put("url", "https://example.com/" + externalId);
        return body;
    }

    private ApiClient.Response submit(String token, Map<String, Object> paper) {
        return api.postJson(PAPERS, token, json(paper));
    }

    /** 提交一篇论文并断言成功,返回本地 id。 */
    private long submitOk(String token, String externalId) {
        ApiClient.Response response = submit(token, paper(externalId, "论文 " + externalId));
        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        return response.at("id").asLong();
    }

    private String registerAndLogin(String username) {
        registerOk(username, username + "@example.com", PASSWORD);
        return loginAndGetToken(username, PASSWORD);
    }

    /** 从一条记录里取出内嵌论文的本地 id。 */
    private static long paperIdOf(JsonNode entry) {
        return entry.get("paper").get("id").asLong();
    }

    /** 把列表响应里每条记录的论文 id 按顺序取出来。 */
    private static List<Long> paperIds(ApiClient.Response response) {
        List<Long> ids = new ArrayList<>();
        response.body().forEach(entry -> ids.add(paperIdOf(entry)));
        return ids;
    }

    private static int intField(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? -1 : value.asInt();
    }

    /**
     * 让下一次写入的时间戳与上一次拉开距离。
     *
     * <p>排序用例需要"后写的确实更晚"。两次 HTTP 往返通常早就隔了几百微秒,
     * 但"通常"正是 F4-3 那条用例翻车的原因 —— 与其赌时钟精度,不如明确隔开。
     * 时间列是 datetime(6),5 毫秒远远够用。
     */
    private static void tick() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待被中断", ex);
        }
    }

    // ── 论文落库 ────────────────────────────────────────────

    @Test
    @DisplayName("F6-1 提交一篇新论文,返回本地 id 与完整元数据")
    void submittingNewPaperReturnsIt() {
        String token = registerAndLogin("alice");

        ApiClient.Response response = submit(token, paper("S2-001", "深度学习综述"));

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(response.at("id").asLong()).isPositive();
        assertThat(response.text("title")).isEqualTo("深度学习综述");
        assertThat(response.text("source")).isEqualTo("semantic_scholar");
        assertThat(response.text("sourceDisplayName")).isEqualTo("Semantic Scholar");
        assertThat(response.text("externalId")).isEqualTo("S2-001");
        assertThat(response.text("abstractText")).isNotBlank();
        assertThat(response.at("publicationYear").asInt()).isEqualTo(2024);
        assertThat(response.at("authors").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("F6-2 同一篇论文重复提交:同一个 id,库里还是一条")
    void resubmittingSamePaperReusesTheRow() {
        String token = registerAndLogin("alice");

        long first = submitOk(token, "S2-001");
        long second = submitOk(token, "S2-001");

        assertThat(second).as("重复提交必须复用同一行,否则同一篇论文会有多个 id").isEqualTo(first);
    }

    @Test
    @DisplayName("F6-3 两个用户提交同一篇论文:仍然只有一行")
    void twoUsersShareTheSamePaperRow() {
        String alice = registerAndLogin("alice");
        String bob = registerAndLogin("bob");

        long fromAlice = submitOk(alice, "S2-001");
        long fromBob = submitOk(bob, "S2-001");

        assertThat(fromBob)
                .as("论文是公共语料,不属于某个用户 —— 各存一份会让推荐里的共现统计重复计数")
                .isEqualTo(fromAlice);
    }

    @Test
    @DisplayName("F6-4 外部 ID 相同但来源不同,视为两篇不同的论文")
    void sameExternalIdFromDifferentSourcesAreDifferentPapers() {
        String token = registerAndLogin("alice");

        long s2 = submitOk(token, "1706.03762");

        Map<String, Object> fromArxiv = paper("1706.03762", "Attention Is All You Need");
        fromArxiv.put("source", "arxiv");
        ApiClient.Response response = submit(token, fromArxiv);

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(response.at("id").asLong())
                .as("不同来源的 ID 空间互不相干,撞号不代表同一篇")
                .isNotEqualTo(s2);
    }

    @Test
    @DisplayName("F6-5 再提交时补上缺失的字段,已有内容会被更新")
    void laterSubmissionFillsInMissingFields() {
        String token = registerAndLogin("alice");

        Map<String, Object> sparse = paper("S2-001", "原标题");
        sparse.remove("abstractText");
        submit(token, sparse);

        Map<String, Object> fuller = paper("S2-001", "补全后的标题");
        ApiClient.Response response = submit(token, fuller);

        assertThat(response.text("title")).isEqualTo("补全后的标题");
        assertThat(response.text("abstractText")).isNotBlank();
    }

    @Test
    @DisplayName("F6-6 后一次提交没带的字段,不会把已有内容抹成空")
    void missingFieldsDoNotEraseStoredMetadata() {
        String token = registerAndLogin("alice");

        submit(token, paper("S2-001", "标题"));

        // 模拟一次信息不全的响应:搜索接口常见的"只给标题和 ID"
        Map<String, Object> sparse = paper("S2-001", "标题");
        sparse.remove("abstractText");
        sparse.remove("venue");
        sparse.remove("publicationYear");
        ApiClient.Response response = submit(token, sparse);

        assertThat(response.text("abstractText"))
                .as("一次信息不全的提交不该让已有的摘要凭空消失 —— 元数据只会变全,不会变少")
                .isNotBlank();
        assertThat(response.text("venue")).isEqualTo("NeurIPS");
        assertThat(response.at("publicationYear").asInt()).isEqualTo(2024);
    }

    @Test
    @DisplayName("F6-7 论文来源非法或必填字段缺失返回 400")
    void invalidPaperIsRejected() {
        String token = registerAndLogin("alice");

        Map<String, Object> unknownSource = paper("S2-001", "标题");
        unknownSource.put("source", "no_such_source");
        assertThat(submit(token, unknownSource).status()).isEqualTo(400);

        Map<String, Object> noTitle = paper("S2-001", "标题");
        noTitle.remove("title");
        assertThat(submit(token, noTitle).status()).isEqualTo(400);

        Map<String, Object> noExternalId = paper("S2-001", "标题");
        noExternalId.remove("externalId");
        assertThat(submit(token, noExternalId).status()).isEqualTo(400);
    }

    // ── 收藏 ────────────────────────────────────────────────

    @Test
    @DisplayName("F6-8 收藏一篇论文后能在列表里读到")
    void favoritedPaperAppearsInList() {
        String token = registerAndLogin("alice");
        long paperId = submitOk(token, "S2-001");

        assertThat(api.post(FAVORITES + "/" + paperId, token).status()).isEqualTo(200);

        ApiClient.Response list = api.get(FAVORITES, token);
        assertThat(list.status()).as(list.describe()).isEqualTo(200);
        assertThat(paperIds(list)).containsExactly(paperId);
        assertThat(list.body().get(0).get("paper").get("title").asString()).isEqualTo("论文 S2-001");
    }

    @Test
    @DisplayName("F6-9 重复收藏是幂等的,不会多出一条")
    void favoritingTwiceIsIdempotent() {
        String token = registerAndLogin("alice");
        long paperId = submitOk(token, "S2-001");

        assertThat(api.post(FAVORITES + "/" + paperId, token).status()).isEqualTo(200);
        ApiClient.Response second = api.post(FAVORITES + "/" + paperId, token);

        assertThat(second.status()).as("前端双击不该报错,也不该产生第二条").isEqualTo(200);
        assertThat(paperIds(api.get(FAVORITES, token))).hasSize(1);
    }

    @Test
    @DisplayName("F6-10 取消收藏后列表里就没有了")
    void removingFavoriteTakesItOutOfTheList() {
        String token = registerAndLogin("alice");
        long paperId = submitOk(token, "S2-001");
        api.post(FAVORITES + "/" + paperId, token);

        ApiClient.Response removed = api.delete(FAVORITES + "/" + paperId, token);

        assertThat(removed.status()).as(removed.describe()).isEqualTo(204);
        assertThat(paperIds(api.get(FAVORITES, token))).isEmpty();
    }

    @Test
    @DisplayName("F6-11 取消一个没收藏过的论文返回 404,而不是假装成功")
    void removingNonExistentFavoriteIsNotFound() {
        String token = registerAndLogin("alice");
        long paperId = submitOk(token, "S2-001");

        assertThat(api.delete(FAVORITES + "/" + paperId, token).status())
                .as("回成功会让前端以为删掉了,下次刷新它又冒出来")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("F6-12 收藏一篇不存在的论文返回 404")
    void favoritingUnknownPaperIsNotFound() {
        String token = registerAndLogin("alice");

        assertThat(api.post(FAVORITES + "/999999", token).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("F6-13 收藏列表按收藏时间倒序")
    void favoritesAreOrderedByMostRecent() {
        String token = registerAndLogin("alice");
        long first = submitOk(token, "S2-001");
        tick();
        long second = submitOk(token, "S2-002");
        tick();
        long third = submitOk(token, "S2-003");

        api.post(FAVORITES + "/" + first, token);
        tick();
        api.post(FAVORITES + "/" + second, token);
        tick();
        api.post(FAVORITES + "/" + third, token);

        assertThat(paperIds(api.get(FAVORITES, token)))
                .as("最近收藏的排在最前面")
                .containsExactly(third, second, first);
    }

    // ── 阅读历史 ────────────────────────────────────────────

    @Test
    @DisplayName("F6-14 记录阅读后出现在历史里,次数为 1")
    void recordedReadAppearsInHistory() {
        String token = registerAndLogin("alice");
        long paperId = submitOk(token, "S2-001");

        ApiClient.Response recorded = api.post(HISTORY + "/" + paperId, token);

        assertThat(recorded.status()).as(recorded.describe()).isEqualTo(200);
        assertThat(intField(recorded.body(), "readCount")).isEqualTo(1);

        ApiClient.Response list = api.get(HISTORY, token);
        assertThat(paperIds(list)).containsExactly(paperId);
        assertThat(intField(list.body().get(0), "readCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("F6-15 同一篇读两次是次数加一,不是两条记录")
    void readingSamePaperTwiceIncrementsTheCount() {
        String token = registerAndLogin("alice");
        long paperId = submitOk(token, "S2-001");

        api.post(HISTORY + "/" + paperId, token);
        ApiClient.Response second = api.post(HISTORY + "/" + paperId, token);

        assertThat(intField(second.body(), "readCount")).isEqualTo(2);
        ApiClient.Response list = api.get(HISTORY, token);
        assertThat(paperIds(list)).as("历史里只该有一条,只是次数变了").containsExactly(paperId);
    }

    @Test
    @DisplayName("F6-16 历史按最近阅读时间倒序,重读会把它顶到最前")
    void historyIsOrderedByMostRecentRead() {
        String token = registerAndLogin("alice");
        long first = submitOk(token, "S2-001");
        tick();
        long second = submitOk(token, "S2-002");

        api.post(HISTORY + "/" + first, token);
        tick();
        api.post(HISTORY + "/" + second, token);
        assertThat(paperIds(api.get(HISTORY, token))).containsExactly(second, first);

        tick();
        api.post(HISTORY + "/" + first, token);

        assertThat(paperIds(api.get(HISTORY, token)))
                .as("重读旧论文应当把它顶到最前面")
                .containsExactly(first, second);
    }

    @Test
    @DisplayName("F6-17 清空历史,且清空一个本来就空的历史也算成功")
    void clearingHistoryIsIdempotent() {
        String token = registerAndLogin("alice");
        long paperId = submitOk(token, "S2-001");
        api.post(HISTORY + "/" + paperId, token);

        ApiClient.Response cleared = api.delete(HISTORY, token);
        assertThat(cleared.status()).as(cleared.describe()).isEqualTo(204);
        assertThat(api.get(HISTORY, token).body()).isEmpty();

        assertThat(api.delete(HISTORY, token).status())
                .as("结果状态一致就该成功 —— 这与取消收藏不同,那里是'没这回事',这里是'已经是空的'")
                .isEqualTo(204);
    }

    @Test
    @DisplayName("F6-18 limit 限制返回条数")
    void historyRespectsLimit() {
        String token = registerAndLogin("alice");
        long first = submitOk(token, "S2-001");
        tick();
        long second = submitOk(token, "S2-002");
        tick();
        long third = submitOk(token, "S2-003");

        api.post(HISTORY + "/" + first, token);
        tick();
        api.post(HISTORY + "/" + second, token);
        tick();
        api.post(HISTORY + "/" + third, token);

        assertThat(paperIds(api.get(HISTORY + "?limit=2", token)))
                .as("取最近的两条")
                .containsExactly(third, second);
    }

    // ── 评分 ────────────────────────────────────────────────

    @Test
    @DisplayName("F6-19 打分后能读回")
    void ratingIsStoredAndReadBack() {
        String token = registerAndLogin("alice");
        long paperId = submitOk(token, "S2-001");

        ApiClient.Response rated = api.putJson(RATINGS + "/" + paperId, token,
                json(Map.of("score", 5)));

        assertThat(rated.status()).as(rated.describe()).isEqualTo(200);
        assertThat(intField(rated.body(), "score")).isEqualTo(5);

        ApiClient.Response list = api.get(RATINGS, token);
        assertThat(paperIds(list)).containsExactly(paperId);
        assertThat(intField(list.body().get(0), "score")).isEqualTo(5);
    }

    @Test
    @DisplayName("F6-20 改分是覆盖,不是多出一条")
    void changingScoreOverwrites() {
        String token = registerAndLogin("alice");
        long paperId = submitOk(token, "S2-001");
        api.putJson(RATINGS + "/" + paperId, token, json(Map.of("score", 2)));

        ApiClient.Response second = api.putJson(RATINGS + "/" + paperId, token, json(Map.of("score", 4)));

        assertThat(second.status()).as(second.describe()).isEqualTo(200);
        ApiClient.Response list = api.get(RATINGS, token);
        assertThat(paperIds(list)).hasSize(1);
        assertThat(intField(list.body().get(0), "score")).isEqualTo(4);
    }

    @Test
    @DisplayName("F6-21 分数越界或缺失返回 400")
    void invalidScoreIsRejected() {
        String token = registerAndLogin("alice");
        long paperId = submitOk(token, "S2-001");
        String path = RATINGS + "/" + paperId;

        assertThat(api.putJson(path, token, json(Map.of("score", 0))).status()).isEqualTo(400);
        assertThat(api.putJson(path, token, json(Map.of("score", 6))).status()).isEqualTo(400);
        assertThat(api.putJson(path, token, json(Map.of())).status())
                .as("漏传分数不能伪装成 0 分")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("F6-22 取消评分;取消没评过的那篇返回 404")
    void removingRating() {
        String token = registerAndLogin("alice");
        long rated = submitOk(token, "S2-001");
        long neverRated = submitOk(token, "S2-002");
        api.putJson(RATINGS + "/" + rated, token, json(Map.of("score", 3)));

        assertThat(api.delete(RATINGS + "/" + rated, token).status()).isEqualTo(204);
        assertThat(api.get(RATINGS, token).body()).isEmpty();

        assertThat(api.delete(RATINGS + "/" + neverRated, token).status()).isEqualTo(404);
    }

    // ── 并发 ────────────────────────────────────────────────

    @Test
    @DisplayName("F6-25 并发提交同一篇新论文:全部成功,且收敛到同一个 id")
    void concurrentSubmissionOfSamePaperIsSafe() throws Exception {
        PaperInput input = new PaperInput("semantic_scholar", "CONCURRENT-001",
                "并发提交的论文", List.of("Alice"), "摘要", 2024, "NeurIPS", null);

        int threads = 8;
        CyclicBarrier startTogether = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);   // 尽量让八个线程同时冲进"查不到就插入"
                    return paperService.resolve(input).getId();
                });
            }

            Set<Long> ids = new HashSet<>();
            for (Future<Long> result : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
                ids.add(result.get());   // 抛异常会在这里浮出来,正是这条用例要挡的
            }

            assertThat(ids)
                    .as("并发首次提交同一篇论文必须收敛到同一行,不能有人拿到 500")
                    .hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ── 鉴权与隔离 ──────────────────────────────────────────

    @Test
    @DisplayName("F6-23 未登录访问这些接口一律 401")
    void allEndpointsRequireAuthentication() {
        assertThat(api.postJson(PAPERS, null, json(paper("S2-001", "标题"))).status()).isEqualTo(401);
        assertThat(api.get(FAVORITES).status()).isEqualTo(401);
        assertThat(api.post(FAVORITES + "/1", null).status()).isEqualTo(401);
        assertThat(api.delete(FAVORITES + "/1", null).status()).isEqualTo(401);
        assertThat(api.get(HISTORY).status()).isEqualTo(401);
        assertThat(api.post(HISTORY + "/1", null).status()).isEqualTo(401);
        assertThat(api.delete(HISTORY, null).status()).isEqualTo(401);
        assertThat(api.get(RATINGS).status()).isEqualTo(401);
        assertThat(api.putJson(RATINGS + "/1", null, json(Map.of("score", 3))).status()).isEqualTo(401);
        assertThat(api.delete(RATINGS + "/1", null).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("F6-24 用户之间的收藏/历史/评分互不影响")
    void libraryIsScopedPerUser() {
        String alice = registerAndLogin("alice");
        String bob = registerAndLogin("bob");
        long shared = submitOk(alice, "S2-001");

        api.post(FAVORITES + "/" + shared, alice);
        api.post(HISTORY + "/" + shared, alice);
        api.putJson(RATINGS + "/" + shared, alice, json(Map.of("score", 5)));

        assertThat(api.get(FAVORITES, bob).body()).as("bob 没收藏过").isEmpty();
        assertThat(api.get(HISTORY, bob).body()).isEmpty();
        assertThat(api.get(RATINGS, bob).body()).isEmpty();

        // 但论文本身是共享的 —— bob 直接引用同一个 id 即可,不需要再提交一次元数据
        assertThat(api.post(FAVORITES + "/" + shared, bob).status()).isEqualTo(200);
        assertThat(paperIds(api.get(FAVORITES, bob))).containsExactly(shared);
    }
}
