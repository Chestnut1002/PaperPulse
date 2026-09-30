package com.paperpulse.reading.dto;

import java.util.List;

/**
 * 一次问答的结果。
 *
 * @param answer       回答正文,里面带 {@code [[§n]]} 标记
 * @param citations    依据。**摘录是 ai-service 从原文直接取的**,不经过模型
 * @param omittedTurns 因为超出上下文预算被丢掉的旧轮次数。丢的时候要让用户知道 ——
 *                     静默截断会让他以为模型没看到的问题它没看到
 */
public record QaResponse(String answer, List<Citation> citations, int omittedTurns) {

    /**
     * @param index   节号,与 {@code answer} 里的 {@code [[§n]]} 对应
     * @param title   节标题
     * @param excerpt 该节开头的原文摘录
     */
    public record Citation(int index, String title, String excerpt) {
    }
}
