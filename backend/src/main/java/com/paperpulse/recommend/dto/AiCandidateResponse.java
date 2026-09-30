package com.paperpulse.recommend.dto;

import java.util.List;

/**
 * ai-service {@code POST /recommend/candidates} 的响应体。
 *
 * <p>与 {@code AiSearchResponse.Paper} 的字段几乎一样,只多一个 {@code matchedTags} ——
 * 但那是**另一个接口的契约**,单独定义比硬塞进检索那个 record 里清楚。
 */
public record AiCandidateResponse(String sourceLabel, List<Candidate> papers) {

    /**
     * @param matchedTags 这篇命中了哪几路检索(即哪几个兴趣)。同一篇能被多个兴趣命中,
     *                    调用方靠它决定"最相关的是哪个兴趣",并据此生成推荐理由
     */
    public record Candidate(
            String source,
            String externalId,
            String doi,
            String arxivId,
            String title,
            List<String> authors,
            String abstractText,
            Integer publicationYear,
            String venue,
            String url,
            Integer citationCount,
            List<String> matchedTags) {
    }
}
