package com.paperpulse.library.dto;

import com.paperpulse.library.PaperRating;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 给一篇论文打分。
 *
 * <p>论文由路径参数指定,请求体里只有分数 —— 因为走到这一步时论文一定已经在库里了
 * (先 {@code POST /api/papers} 落库拿到 id)。不像收藏,那里可能遇到全新的论文,
 * 所以那边用的是 id 而不是元数据,两边一致。
 *
 * @param score 1~5
 */
public record RatingRequest(

        // 用 Integer 而不是 int:漏传分数会被基本类型反序列化成 0,
        // 于是"没填"伪装成"打了 0 分",报错信息也会指向错误的原因。F5 的权重字段踩过同一个坑。
        @NotNull(message = "评分不能为空")
        @Min(value = PaperRating.MIN_SCORE, message = "评分需在 1~5 之间")
        @Max(value = PaperRating.MAX_SCORE, message = "评分需在 1~5 之间")
        Integer score) {
}
