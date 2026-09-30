package com.paperpulse.recommend.dto;

import com.paperpulse.paper.dto.PaperResponse;

import java.util.List;

/**
 * 一次推荐的结果。
 *
 * @param sourceLabel   候选来自哪些数据源
 * @param diversityRatio 本次生效的探索位比例(回显,便于调参时确认真的生效了)
 * @param hint          给用户的一句提示。目前只有一种情况会用到:**还没选兴趣标签**
 * @param recommendations 推荐列表
 */
public record RecommendationResponse(
        String sourceLabel,
        double diversityRatio,
        String hint,
        List<Item> recommendations) {

    /**
     * @param score       排序分。**返回给前端是为了让排序可验证** —— 不必理解具体量纲
     * @param reason      为什么推荐这一篇。这是 REQ-005 的最简版本:
     *                    从算法里顺手拿得到(命中了哪个兴趣、权重多少),不额外花成本
     * @param matchedTags 命中的兴趣标签 key
     */
    public record Item(
            PaperResponse paper,
            double score,
            String reason,
            List<String> matchedTags) {
    }
}
