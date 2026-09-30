package com.paperpulse.search.dto;

import com.paperpulse.paper.dto.PaperResponse;

import java.util.List;

/**
 * 检索结果。
 *
 * <p>把 Agent 的拆解结果({@code keywords / yearFrom / yearTo / rationale})一并返回,
 * 而不是只给论文列表 —— 用户输入的是"找 2024 年以后对比学习在推荐系统里的应用",
 * 拿回一堆英文标题,中间那步"我把它理解成了什么"如果不告诉他,
 * 结果不理想时就无从判断是拆解错了还是数据源没有。
 *
 * <p>{@code yearFrom / yearTo} 尤其要说:它们是**拆解出来的筛选条件**,
 * 用户看不到就会以为"2024 年以后"没生效,而实际上结果可能只是恰好都在这之后。
 *
 * @param query       用户原始输入
 * @param keywords    实际用于检索的英文关键词
 * @param yearFrom    拆解出的起始年份(含),不限为 null
 * @param yearTo      拆解出的结束年份(含),不限为 null —— 通常为 null,只有"2020 到 2023 年"这类才两边都有
 * @param rationale   Agent 的拆解理由(中文)
 * @param sourceLabel 实际命中的数据源展示名(降级链走到哪就是谁)
 * @param papers      已落库、带本地 id 的论文
 */
public record PaperSearchResponse(
        String query,
        String keywords,
        Integer yearFrom,
        Integer yearTo,
        String rationale,
        String sourceLabel,
        List<PaperResponse> papers) {
}
