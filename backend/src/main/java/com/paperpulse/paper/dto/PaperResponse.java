package com.paperpulse.paper.dto;

import com.paperpulse.paper.Paper;

import java.util.List;

/**
 * 一篇论文对外呈现的样子。
 *
 * <p>同时带 {@code source}(key)与 {@code sourceDisplayName}(中文名):
 * 前者供前端回传,后者供直接展示 —— 让前端自己去维护一份 key 到中文名的映射表,
 * 等于把词表复制到了第二处,以后加来源就会漏改。
 *
 * @param id                本地 id,收藏 / 阅读 / 打分都用它
 * @param source            来源库 key
 * @param sourceDisplayName 来源库展示名
 * @param externalId        来源库里的 ID
 * @param title             标题
 * @param authors           作者名列表
 * @param abstractText      摘要,可能为 null
 * @param publicationYear   发表年份,可能为 null
 * @param venue             会议或期刊名,可能为 null
 * @param url               原文链接,可能为 null
 */
public record PaperResponse(
        Long id,
        String source,
        String sourceDisplayName,
        String externalId,
        String title,
        List<String> authors,
        String abstractText,
        Integer publicationYear,
        String venue,
        String url) {

    public static PaperResponse of(Paper paper) {
        return new PaperResponse(
                paper.getId(),
                paper.getSource().key(),
                paper.getSource().displayName(),
                paper.getExternalId(),
                paper.getTitle(),
                paper.getAuthors(),
                paper.getAbstractText(),
                paper.getPublicationYear(),
                paper.getVenue(),
                paper.getUrl());
    }
}
