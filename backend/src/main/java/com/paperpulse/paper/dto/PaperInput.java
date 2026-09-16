package com.paperpulse.paper.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 提交一篇论文的元数据。
 *
 * <p>用在 {@code POST /api/papers}:前端从检索结果里拿到论文后,先把元数据交给后端落库,
 * 拿到本地 id 再去收藏 / 记录阅读 / 打分。**幂等** —— 同一个 {@code (source, externalId)}
 * 提交多次只会有一行。
 *
 * <p>{@code source} 用字符串而不是直接收枚举:这样"未知来源"能由
 * {@link com.paperpulse.paper.PaperSource#fromKey} 抛出一句明确的
 * {@code 未知的论文来源: xxx},而不是让 Jackson 在反序列化阶段报一句框架内部的错。
 *
 * @param source          来源库的 key(见 {@code PaperSource})
 * @param externalId      来源库里的 ID
 * @param title           标题
 * @param authors         作者名列表,可为空
 * @param abstractText    摘要,可空
 * @param publicationYear 发表年份,可空
 * @param venue           会议或期刊名,可空
 * @param url             原文链接,可空
 */
public record PaperInput(

        @NotBlank(message = "论文来源不能为空")
        @Size(max = 32, message = "论文来源过长")
        String source,

        @NotBlank(message = "论文的外部 ID 不能为空")
        @Size(max = 128, message = "外部 ID 过长")
        String externalId,

        @NotBlank(message = "论文标题不能为空")
        @Size(max = 512, message = "标题过长")
        String title,

        List<@NotBlank(message = "作者名不能为空") String> authors,

        String abstractText,

        // 年份用 Integer:未知就是 null,不该被当成 0。
        // 下限取 1000 是为了挡住明显的手滑,不是严谨的文献学判断。
        @Min(value = 1000, message = "发表年份不合法")
        @Max(value = 2200, message = "发表年份不合法")
        Integer publicationYear,

        @Size(max = 256, message = "会议/期刊名过长")
        String venue,

        @Size(max = 512, message = "链接过长")
        String url) {

    /**
     * 紧凑构造函数:把 null 的作者列表归一成空列表。
     *
     * <p>这样 {@link com.paperpulse.paper.Paper#applyMetadata} 里判断"作者是否为空"
     * 只需要处理一种情况,不用同时防着 null 和空列表。
     */
    public PaperInput {
        authors = authors == null ? List.of() : List.copyOf(authors);
    }
}
