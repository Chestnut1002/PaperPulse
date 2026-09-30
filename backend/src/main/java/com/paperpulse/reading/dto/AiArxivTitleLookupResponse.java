package com.paperpulse.reading.dto;

import java.util.List;

/**
 * ai-service {@code POST /lookup/arxiv-by-title} 的响应。
 *
 * <p><b>返回候选列表而不是"找到的那一篇"</b>:判"是不是同一篇"的规则
 * ({@code PaperMatcher}:标题 + 作者 + 年份)在 Java 侧 —— Python 只负责把候选捞回来。
 * 规则只有一份,两边各写一套迟早会不一致。
 *
 * <p><b>空列表是正常结果</b>:arXiv 上确实没有这篇的预印本,不是错误。
 */
public record AiArxivTitleLookupResponse(List<AiPaper> candidates) {

    public AiArxivTitleLookupResponse {
        // null 与空列表在这里是同一件事:都是"没有候选"。统一成空列表,
        // 调用方就不必先判 null 再判空 —— 少一处可以写错的地方
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
