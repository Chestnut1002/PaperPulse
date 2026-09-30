package com.paperpulse.reading.dto;

/**
 * ai-service {@code POST /lookup/arxiv} 的响应。
 *
 * @param found 有没有这个编号。**用布尔而不是 404** —— 编号写错是常见情况,
 *              调用方据此给一句人话即可,没必要把它当异常
 */
public record AiArxivLookupResponse(boolean found, AiPaper paper) {
}
