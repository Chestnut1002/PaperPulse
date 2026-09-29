package com.paperpulse.search.dto;

import java.util.List;

/**
 * ai-service {@code POST /search} 的响应体。
 *
 * <p>这是**外部服务的契约**,不是我们的领域模型 —— 所以单独定义,不和 {@code Paper} 之类的领域对象混用。
 * 字段名与 Python 侧完全一致(那边刻意用了 camelCase),两边都不必写映射代码。
 *
 * @param query       用户原始输入
 * @param plan        Agent 的拆解结果
 * @param sourceLabel 实际命中的数据源展示名
 * @param papers      检索到的论文
 */
public record AiSearchResponse(String query, Plan plan, String sourceLabel, List<Paper> papers) {

    /**
     * @param keywords  实际用于检索的英文关键词
     * @param yearFrom  起始年份,不限为 null
     * @param yearTo    结束年份,不限为 null
     * @param rationale 拆解理由,中文
     */
    public record Plan(String keywords, Integer yearFrom, Integer yearTo, String rationale) {
    }

    /**
     * @param source 来源库 key,与 {@code PaperSource.key()} 一致
     */
    public record Paper(
            String source,
            String externalId,
            String title,
            List<String> authors,
            String abstractText,
            Integer publicationYear,
            String venue,
            String url,
            Integer citationCount) {
    }
}
