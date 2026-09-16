package com.paperpulse.paper;

import com.paperpulse.common.ApiException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 论文的来源库(F6)。
 *
 * <p><b>为什么来源要和外部 ID 一起构成唯一标识:</b>不同来源各有自己的一套 ID,
 * 彼此可能撞号 —— arXiv 的 {@code 1706.03762} 和别处的同号字符串是两篇不同的论文。
 * 只按外部 ID 去重会把它们错认成同一篇。
 *
 * <p>同一个 {@code key()} 规则与 {@link com.paperpulse.interest.InterestTag} 保持一致:
 * 小写枚举名,存库、走 API 的都是它。
 */
public enum PaperSource {

    /** 主力来源。本机网络可达,且提供 {@code fieldsOfStudy} 等结构化字段。 */
    SEMANTIC_SCHOLAR("Semantic Scholar"),

    /** 备选来源。API 在本机不可达,保留枚举值以便将来切换或补数据。 */
    ARXIV("arXiv"),

    /** 降级备选,元数据字段比 S2 少。 */
    CROSSREF("Crossref");

    private final String displayName;

    PaperSource(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 稳定标识,存库与 API 用。
     *
     * <p><b>必须带 {@link Locale#ROOT}:</b>不带 Locale 的 {@code toLowerCase()} 在土耳其语环境下
     * 会把 {@code I} 变成无点的 {@code ı},于是 {@code SEMANTIC_SCHOLAR} 会得到
     * {@code semantıc_scholar},和库里存的对不上。这个坑在 F5 的排序比较器上已经踩过一次。
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    /** 按 key 查来源,查不到抛 400 —— 未知来源是客户端的参数错误,不该静默当成默认值。 */
    public static PaperSource fromKey(String key) {
        return findByKey(key).orElseThrow(() -> ApiException.badRequest("未知的论文来源:" + key));
    }

    public static Optional<PaperSource> findByKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(source -> source.key().equals(key))
                .findFirst();
    }
}
