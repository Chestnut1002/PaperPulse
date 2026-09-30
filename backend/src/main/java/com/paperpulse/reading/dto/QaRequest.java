package com.paperpulse.reading.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 就一篇论文提一个问题。
 *
 * <p><b>对话历史由前端带上</b>,后端与 ai-service 都不维持会话状态 ——
 * 好处是服务重启不丢对话,而且"截断旧轮次"这件事发生在发送之前,规则看得见。
 *
 * @param question 本次提问
 * @param history  之前的若干轮,按时间顺序;前端只需要带最近几轮
 */
public record QaRequest(

        @NotBlank(message = "问题不能为空")
        @Size(min = 2, max = 2000, message = "问题长度需在 2~2000 之间")
        String question,

        List<Turn> history) {

    /**
     * @param role    "user" 或 "assistant"
     * @param content 该轮的内容
     */
    public record Turn(String role, String content) {
    }

    /** 把 null 归一成空列表,免得下游到处判空。 */
    public QaRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
