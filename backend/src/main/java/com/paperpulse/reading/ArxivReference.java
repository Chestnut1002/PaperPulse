package com.paperpulse.reading;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从用户粘贴的内容里抽出 arXiv 编号。
 *
 * <p>用户手上有什么就贴什么:可能是编号本身,也可能是从浏览器地址栏复制的链接。
 * 让他先自己把编号抠出来是不合理的。
 *
 * <p>纯逻辑,不碰网络 —— 输入形态很多(带链接、带版本号、老式编号),这类解析最容易出错,
 * 抽出来才能单测。
 */
public final class ArxivReference {

    /**
     * 新式编号(2007 年以后):{@code 2502.19271}。
     */
    private static final Pattern NEW_STYLE = Pattern.compile("\\b(\\d{4}\\.\\d{4,5})(?:v\\d+)?\\b");

    /**
     * 老式编号(2007 年以前):{@code cs/0701001}、{@code math.GT/0309136}。
     *
     * <p>分类前缀必须带斜杠才认 —— 否则一段普通文字里的 `and/or` 之类也会被当成编号。
     */
    private static final Pattern OLD_STYLE = Pattern.compile(
            "\\b([a-z][a-z-]*(?:\\.[a-z]{2})?/\\d{7})(?:v\\d+)?\\b",
            Pattern.CASE_INSENSITIVE);

    private ArxivReference() {
    }

    /**
     * 抽出编号;**抽不出来返回 null**。
     *
     * <p>不抛异常:用户可能贴错东西,那是常见情况,调用方给一句人话就好。
     *
     * <p>结果统一小写并**去掉版本号** —— {@code 2502.19271v2} 与 {@code 2502.19271}
     * 是同一篇的不同修订,带着版本号会认不出来。
     */
    public static String extract(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String text = raw.trim();
        Matcher modern = NEW_STYLE.matcher(text);
        if (modern.find()) {
            return modern.group(1).toLowerCase(Locale.ROOT);
        }

        // 匹配**原文本**再转小写,不要先转小写再匹配:
        // 老式编号的分类后缀在原文里是大写(`math.GT/0309136`),先转小写会让整个前缀对不上,
        // 于是正则从中间的 `gt` 开始匹配 —— 得到一个丢了前缀的错误编号。
        Matcher legacy = OLD_STYLE.matcher(text);
        if (legacy.find()) {
            return legacy.group(1).toLowerCase(Locale.ROOT);
        }
        return null;
    }
}
