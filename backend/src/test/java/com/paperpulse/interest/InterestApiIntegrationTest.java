package com.paperpulse.interest;

import com.paperpulse.support.AbstractIntegrationTest;
import com.paperpulse.support.ApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F5:兴趣标签。
 *
 * <p>重点覆盖三类容易出问题的地方:
 * <ul>
 *   <li><b>全量替换的语义</b> —— 少传的标签要真的消失,空数组要真的清空</li>
 *   <li><b>写入次序</b> —— 改权重时标签没变,先插后删会撞唯一约束(见 {@link UserInterestRepository#deleteAllByUserId})</li>
 *   <li><b>校验发生在动数据之前</b> —— 参数不合法时,旧数据必须原封不动</li>
 * </ul>
 */
@DisplayName("F5 兴趣标签")
class InterestApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "secret123";
    private static final String MY_INTERESTS = "/api/users/me/interests";
    private static final String CATALOG = "/api/interests";

    // ── 小工具 ──────────────────────────────────────────────

    /** 提交一组 (标签, 权重)。 */
    private ApiClient.Response putInterests(String token, Object... tagWeightPairs) {
        List<Map<String, Object>> interests = new ArrayList<>();
        for (int i = 0; i < tagWeightPairs.length; i += 2) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tag", tagWeightPairs[i]);
            item.put("weight", tagWeightPairs[i + 1]);
            interests.add(item);
        }
        return api.putJson(MY_INTERESTS, token, json(Map.of("interests", interests)));
    }

    /** 取出当前兴趣,归一化成 {标签=权重} 便于断言。 */
    private Map<String, Integer> interestsOf(String token) {
        ApiClient.Response response = api.get(MY_INTERESTS, token);
        assertThat(response.status()).as(response.describe()).isEqualTo(200);

        Map<String, Integer> result = new LinkedHashMap<>();
        response.body().forEach(node -> result.put(field(node, "tag"), node.get("weight").asInt()));
        return result;
    }

    /** 从 JSON 节点上取字符串字段。字段缺失返回 null,让断言去报错而不是抛 NPE。 */
    private static String field(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asString();
    }

    private String registerAndLogin(String username) {
        registerOk(username, username + "@example.com", PASSWORD);
        return loginAndGetToken(username, PASSWORD);
    }

    /** 取词表里前 n 个标签的 key —— 避免在用例里硬编码一长串标签名。 */
    private List<String> firstTagKeys(String token, int n) {
        List<String> keys = new ArrayList<>();
        for (JsonNode category : api.get(CATALOG, token).at("categories")) {
            for (JsonNode tag : category.get("tags")) {
                if (keys.size() < n) {
                    keys.add(field(tag, "key"));
                }
            }
        }
        return keys;
    }

    /** 把标签 key 列表摊成 (key, weight, key, weight, ...) 以便喂给 {@link #putInterests}。 */
    private static Object[] withWeight(List<String> keys, int weight) {
        Object[] pairs = new Object[keys.size() * 2];
        for (int i = 0; i < keys.size(); i++) {
            pairs[i * 2] = keys.get(i);
            pairs[i * 2 + 1] = weight;
        }
        return pairs;
    }

    // ── 用例 ────────────────────────────────────────────────

    @Test
    @DisplayName("F5-1 标签词表按分类返回,并带上权重区间与数量上限")
    void catalogListsTagsGroupedByCategory() {
        String token = registerAndLogin("alice");

        ApiClient.Response response = api.get(CATALOG, token);

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(response.at("minWeight").asInt()).isEqualTo(UserInterest.MIN_WEIGHT);
        assertThat(response.at("maxWeight").asInt()).isEqualTo(UserInterest.MAX_WEIGHT);
        assertThat(response.at("maxTags").asInt()).isEqualTo(InterestService.MAX_TAGS);

        JsonNode categories = response.at("categories");
        assertThat(categories.size()).as("词表不能是空的").isPositive();

        for (JsonNode category : categories) {
            String name = field(category, "name");
            assertThat(name).isNotBlank();
            assertThat(category.get("tags").size()).as("分类 %s 下没有标签", name).isPositive();
            for (JsonNode tag : category.get("tags")) {
                assertThat(field(tag, "key")).isNotBlank();
                assertThat(field(tag, "displayName")).isNotBlank();
            }
        }
        // 与枚举体量对得上,防止哪天漏序列化了一部分
        assertThat(countTags(categories)).isEqualTo(InterestTag.values().length);
    }

    private int countTags(JsonNode categories) {
        int total = 0;
        for (JsonNode category : categories) {
            total += category.get("tags").size();
        }
        return total;
    }

    @Test
    @DisplayName("F5-2 未登录访问兴趣接口返回 401")
    void interestsRequireAuthentication() {
        assertThat(api.get(CATALOG).status()).isEqualTo(401);
        assertThat(api.get(MY_INTERESTS).status()).isEqualTo(401);
        assertThat(api.exchange("PUT", MY_INTERESTS, Map.of("Content-Type", "application/json"),
                json(Map.of("interests", List.of()))).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("F5-3 新用户的兴趣列表为空")
    void newUserHasNoInterests() {
        String token = registerAndLogin("alice");

        assertThat(interestsOf(token)).isEmpty();
    }

    @Test
    @DisplayName("F5-4 提交后能原样读回,权重与展示名都对")
    void submittedInterestsCanBeReadBack() {
        String token = registerAndLogin("alice");

        ApiClient.Response put = putInterests(token, "recommender_system", 5, "large_language_model", 3);
        assertThat(put.status()).as(put.describe()).isEqualTo(200);

        assertThat(interestsOf(token))
                .containsExactlyInAnyOrderEntriesOf(Map.of("recommender_system", 5, "large_language_model", 3));

        // 读回时展示名与分类由服务端查词表补上,不存库
        JsonNode first = api.get(MY_INTERESTS, token).body().get(0);
        assertThat(field(first, "displayName")).isNotBlank();
        assertThat(field(first, "category")).isNotBlank();
    }

    @Test
    @DisplayName("F5-5 PUT 是替换:没提交的标签会消失")
    void putReplacesRatherThanAppends() {
        String token = registerAndLogin("alice");
        putInterests(token, "recommender_system", 5, "information_retrieval", 4);

        putInterests(token, "information_retrieval", 4);

        assertThat(interestsOf(token))
                .as("第二次没提交 recommender_system,它就该没了")
                .containsExactlyInAnyOrderEntriesOf(Map.of("information_retrieval", 4));
    }

    @Test
    @DisplayName("F5-6 同一标签改权重:标签没变,只改数值(会撞上先插后删的唯一约束)")
    void updatingWeightOfSameTagWorks() {
        String token = registerAndLogin("alice");
        putInterests(token, "recommender_system", 2);

        ApiClient.Response second = putInterests(token, "recommender_system", 5);

        // 这条用例专门盯着"全量替换"最容易踩的坑:标签没变,只是权重变了。
        // 若用派生删除 + saveAll,Hibernate 会先插新行再删旧行,(user_id, tag_key) 唯一约束当场报错。
        assertThat(second.status()).as(second.describe()).isEqualTo(200);
        assertThat(interestsOf(token)).containsExactlyInAnyOrderEntriesOf(Map.of("recommender_system", 5));
    }

    @Test
    @DisplayName("F5-7 重复提交同一组标签是幂等的")
    void repeatedPutIsIdempotent() {
        String token = registerAndLogin("alice");
        putInterests(token, "nlp", 3, "computer_vision", 4);
        putInterests(token, "nlp", 3, "computer_vision", 4);

        assertThat(interestsOf(token))
                .containsExactlyInAnyOrderEntriesOf(Map.of("nlp", 3, "computer_vision", 4));
    }

    @Test
    @DisplayName("F5-8 传空数组清空全部兴趣")
    void emptyListClearsAllInterests() {
        String token = registerAndLogin("alice");
        putInterests(token, "nlp", 3);

        ApiClient.Response cleared = putInterests(token);

        assertThat(cleared.status()).as(cleared.describe()).isEqualTo(200);
        assertThat(interestsOf(token)).isEmpty();
    }

    @Test
    @DisplayName("F5-9 权重越界返回 400")
    void weightOutOfRangeIsRejected() {
        String token = registerAndLogin("alice");

        assertThat(putInterests(token, "nlp", UserInterest.MIN_WEIGHT - 1).status()).isEqualTo(400);
        assertThat(putInterests(token, "nlp", UserInterest.MAX_WEIGHT + 1).status()).isEqualTo(400);
    }

    @Test
    @DisplayName("F5-10 缺 weight 返回 400(而不是被当成 0 权重悄悄存下)")
    void missingWeightIsRejected() {
        String token = registerAndLogin("alice");

        ApiClient.Response response = api.putJson(MY_INTERESTS, token,
                json(Map.of("interests", List.of(Map.of("tag", "nlp")))));

        assertThat(response.status()).as(response.describe()).isEqualTo(400);
    }

    @Test
    @DisplayName("F5-11 未知标签返回 400,且旧数据不受影响")
    void unknownTagIsRejectedAndLeavesExistingDataIntact() {
        String token = registerAndLogin("alice");
        putInterests(token, "nlp", 3);

        ApiClient.Response response = putInterests(token, "nlp", 3, "no_such_tag", 5);

        assertThat(response.status()).as(response.describe()).isEqualTo(400);
        assertThat(interestsOf(token))
                .as("校验必须先于删数据,否则一次手滑就把已存的兴趣清空了")
                .containsExactlyInAnyOrderEntriesOf(Map.of("nlp", 3));
    }

    @Test
    @DisplayName("F5-12 同一次请求里标签重复返回 400")
    void duplicateTagInSameRequestIsRejected() {
        String token = registerAndLogin("alice");

        ApiClient.Response response = putInterests(token, "nlp", 3, "nlp", 5);

        assertThat(response.status()).as(response.describe()).isEqualTo(400);
    }

    @Test
    @DisplayName("F5-13 超过数量上限返回 400")
    void exceedingMaxTagsIsRejected() {
        String token = registerAndLogin("alice");
        List<String> keys = firstTagKeys(token, InterestService.MAX_TAGS + 1);
        assertThat(keys).as("词表标签数不足以构造这个用例").hasSize(InterestService.MAX_TAGS + 1);

        assertThat(putInterests(token, withWeight(keys, 3)).status()).isEqualTo(400);
    }

    @Test
    @DisplayName("F5-14 恰好达到上限是允许的(边界不能也一起挡掉)")
    void exactlyMaxTagsIsAccepted() {
        String token = registerAndLogin("alice");
        List<String> keys = firstTagKeys(token, InterestService.MAX_TAGS);

        ApiClient.Response response = putInterests(token, withWeight(keys, 3));

        assertThat(response.status()).as(response.describe()).isEqualTo(200);
        assertThat(interestsOf(token)).hasSize(InterestService.MAX_TAGS);
    }

    @Test
    @DisplayName("F5-15 用户之间互不影响")
    void interestsAreScopedPerUser() {
        String alice = registerAndLogin("alice");
        String bob = registerAndLogin("bob");

        putInterests(alice, "nlp", 5);
        putInterests(bob, "computer_vision", 2);

        assertThat(interestsOf(alice)).containsExactlyInAnyOrderEntriesOf(Map.of("nlp", 5));
        assertThat(interestsOf(bob)).containsExactlyInAnyOrderEntriesOf(Map.of("computer_vision", 2));
    }
}
