package com.paperpulse.paper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 把 {@code List<String>} 以 JSON 数组的形式存进一个文本列。
 *
 * <p>用于论文的作者列表。**不用逗号拼接**:作者名里本来就可能有逗号
 * (「Smith, John」这种「姓, 名」写法很常见),拼完就再也拆不回来了。
 * 换任何分隔符都只是把一个不确定的问题推给下一个人。
 *
 * <p><b>这是一个刻意的简化。</b>作者列表现在只用于展示,不需要按作者查询。
 * 等到 REQ-004 真的要做"同作者"这类特征时,正确的做法是加一张
 * {@code paper_author} 关联表,而不是在这里做字符串匹配 —— 那时本转换器会被替换掉。
 *
 * <p>用 Jackson 而不是手写拼接:转义、空串、Unicode 这些边界它已经处理过了。
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    /** JsonMapper 是线程安全的,建一次即可。 */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return MAPPER.writeValueAsString(attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return List.of(MAPPER.readValue(dbData, String[].class));
        } catch (RuntimeException ex) {
            // 存进去的一定是本转换器写的合法 JSON,进到这里说明有人手工改过库。
            // 不抛异常:整个列表读不出来不该让一篇论文变得不可读,退回空列表即可。
            return List.of();
        }
    }
}
