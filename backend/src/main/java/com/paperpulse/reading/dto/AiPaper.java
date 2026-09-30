package com.paperpulse.reading.dto;

import java.util.List;

/**
 * ai-service 返回的一篇论文的元数据。
 *
 * <p><b>两条"取元数据"的路共用同一个形状</b>:按编号取({@link AiArxivLookupResponse})
 * 与按标题反查({@link AiArxivTitleLookupResponse})。各自内嵌一份的话,
 * 第三个调用方出现时就得先问"我该用哪个"—— 而它们本该是同一个东西。
 *
 * <p>字段与 {@code PaperInput} 对齐,拿到后可直接落库。
 */
public record AiPaper(
        String source,
        String externalId,
        String doi,
        String arxivId,
        String title,
        List<String> authors,
        String abstractText,
        Integer publicationYear,
        String venue,
        String url) {
}
