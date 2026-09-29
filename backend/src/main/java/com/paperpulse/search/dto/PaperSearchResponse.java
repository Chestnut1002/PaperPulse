package com.paperpulse.search.dto;

import com.paperpulse.paper.dto.PaperResponse;

import java.util.List;

/**
 * 检索结果。
 *
 * <p>把 Agent 的拆解结果({@code keywords / rationale})一并返回,而不是只给论文列表 ——
 * 用户输入的是"找 2024 年以后对比学习在推荐系统里的应用",拿回一堆英文标题,
 * 中间那步"我把它理解成了什么"如果不告诉他,结果不理想时就无从判断是拆解错了还是数据源没有。
 *
 * @param query       用户原始输入
 * @param keywords    实际用于检索的英文关键词
 * @param rationale   Agent 的拆解理由(中文)
 * @param sourceLabel 实际命中的数据源展示名(降级链走到哪就是谁)
 * @param papers      已落库、带本地 id 的论文
 */
public record PaperSearchResponse(
        String query,
        String keywords,
        String rationale,
        String sourceLabel,
        List<PaperResponse> papers) {
}
