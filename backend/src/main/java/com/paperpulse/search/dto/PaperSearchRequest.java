package com.paperpulse.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 一次自然语言检索请求。
 *
 * @param query 自然语言描述的检索需求,中文英文都可以
 * @param limit 最多返回几篇;不传用默认值
 */
public record PaperSearchRequest(

        @NotBlank(message = "检索内容不能为空")
        @Size(min = 2, max = 500, message = "检索内容长度需在 2~500 之间")
        String query,

        // 用 Integer 而不是 int:漏传时是 null,由服务层填默认值,
        // 而不会被反序列化成 0 再报一句莫名其妙的"limit 需大于 0"
        @Min(value = 1, message = "返回条数需在 1~20 之间")
        @Max(value = 20, message = "返回条数需在 1~20 之间")
        Integer limit) {
}
