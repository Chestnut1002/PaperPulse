package com.paperpulse.reading.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用一段用户粘贴的内容打开一篇论文。
 *
 * <p>字段叫 `reference` 而不是 `arxivId`:用户贴的可能是编号、链接,甚至是一句夹着编号的话 ——
 * 让前端先解析是不合理的,解析的规则只该有一份(在 {@code ArxivReference} 里)。
 *
 * @param reference 用户粘贴的内容
 */
public record ArxivOpenRequest(

        @NotBlank(message = "请粘贴 arXiv 编号或链接")
        @Size(max = 512, message = "内容过长")
        String reference) {
}
