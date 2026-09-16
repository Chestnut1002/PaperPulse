package com.paperpulse.interest.dto;

import com.paperpulse.interest.InterestTag;
import com.paperpulse.interest.UserInterest;

import java.util.List;
import java.util.Map;

/**
 * 兴趣标签词表,供前端渲染选择器。
 *
 * <p>把 {@code minWeight / maxWeight / maxTags} 一起返回,而不是让前端自己写死 ——
 * 这些限制将来可能调整,返回来前端就不用跟着改代码。前端也不必再排一次序:分类与标签
 * 都按后端给定的顺序返回。
 *
 * <p>刻意不暴露标签背后的 Semantic Scholar 映射(过滤字段与检索词):那是检索/推荐模块的内部细节,
 * 前端拿不到也用不上,暴露出去只会变成一份需要同步维护的隐式契约。
 */
public record InterestCatalogResponse(
        int minWeight,
        int maxWeight,
        int maxTags,
        List<Category> categories) {

    public record Category(String name, List<Tag> tags) {
    }

    public record Tag(String key, String displayName) {
    }

    public static InterestCatalogResponse from(Map<String, List<InterestTag>> grouped, int maxTags) {
        List<Category> categories = grouped.entrySet().stream()
                .map(entry -> new Category(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(tag -> new Tag(tag.key(), tag.displayName()))
                                .toList()))
                .toList();

        return new InterestCatalogResponse(
                UserInterest.MIN_WEIGHT, UserInterest.MAX_WEIGHT, maxTags, categories);
    }
}
