package com.paperpulse.paper;

import java.util.Locale;

/**
 * DOI 的规范化。
 *
 * <p>DOI 大小写不敏感,而且各家返回的形式不一(裸串、带 {@code https://doi.org/} 前缀、
 * 带 {@code doi:} 前缀)。不归一的话,同一篇论文会因为写法不同被当成两篇 ——
 * 而这个字段正是用来跨源认人的。
 *
 * <p>与 ai-service 的 {@code normalize_doi} 保持同一套规则:两边都是数据的入口
 * (检索结果与客户端提交),哪一边都不该把没规整的值写进库。
 */
public final class Doi {

    private static final String[] PREFIXES = {"https://doi.org/", "http://doi.org/", "doi:"};

    /** 必须是 10.xxxx/yyy 的形状 —— 只做前缀与大小写规整,不做连通性校验。 */
    private static final String DOI_PREFIX = "10.";

    private Doi() {
    }

    /**
     * 规范化 DOI;不像 DOI 就返回 null。
     *
     * <p><b>不像就返回 null 而不是原样返回</b>:兜底一个假身份,会把两篇不同的论文合并成一篇,
     * 那比多存一行糟糕得多 —— 前者是静默的数据损坏,后者只是浪费一行。
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        // 必须带 Locale.ROOT:不带的话土耳其语环境下 "DOI".toLowerCase() 会得到无点的 ı
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (String prefix : PREFIXES) {
            if (value.startsWith(prefix)) {
                value = value.substring(prefix.length());
                break;
            }
        }

        if (!value.startsWith(DOI_PREFIX) || !value.contains("/")) {
            return null;
        }
        return value;
    }
}
