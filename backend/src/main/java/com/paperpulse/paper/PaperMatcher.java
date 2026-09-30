package com.paperpulse.paper;

import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 判定两条记录是不是同一篇论文 —— 跨源合并的规则。
 *
 * <p><b>纯逻辑,不碰数据库</b>:规则是这里最容易出错的地方(合错了是静默的数据损坏),
 * 抽出来才能直接单测,而不是只能靠端到端碰运气。
 *
 * <p><b>规则取向是"宁可漏合并,不可错合并"。</b>
 * 漏掉的代价是多一行、用户看着有点烦;错合并的代价是两篇不同的论文变成一篇,
 * 收藏与评分挂到错误的地方 —— 那是不可逆的。
 *
 * <p>三条实测依据(脚本 {@code scratch/duplicate_probe.py},60 篇样本):
 * 标题完全相同的 21 篇里,作者也对得上的有 20 篇 —— 说明加上作者这一条确实挡掉了会合错的那次;
 * 漏网的主要是括号后缀(如 {@code IGNiteR … (Extended Version)})与标题里的 HTML 标签。
 */
public final class PaperMatcher {

    /**
     * 允许的年份差。
     *
     * <p>预印本到正式发表通常是 1~2 年,给到 3 年留余量;再宽就挡不住
     * "同一位作者不同时期写了同名论文"这种极小概率情况了。
     */
    public static final int YEAR_TOLERANCE = 3;

    /** 换行、制表符等一律当空白 */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** 只保留字母与数字 —— 标点、连字符、全角符号的差异不该影响判断 */
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    /**
     * 括号里的"版本标记"。**只匹配已知的几种,而不是去掉所有括号内容** ——
     * 有些标题的括号本身就是标题的一部分(比如 "BERT (Bidirectional Encoder …)")。
     */
    private static final Pattern VERSION_SUFFIX = Pattern.compile(
            "\\s*[\\[(]\\s*(?:extended(?:\\s+(?:version|abstract))?|preprint|"
                    + "version\\s*\\d+(?:\\.\\d+)?|v\\d+(?:\\.\\d+)?|"
                    + "technical\\s+report|short\\s+paper|full\\s+paper|early\\s+access)"
                    + "\\s*[\\])]\\s*$",
            Pattern.CASE_INSENSITIVE);

    private PaperMatcher() {
    }

    /**
     * 标题的归一化形式,用来比较,也存进 {@code paper.title_key} 供查询。
     *
     * <p>顺序有讲究:**先还原 HTML 转义,再去标签**。反过来做的话,`&lt;i&gt;SiReN&lt;/i&gt;`
     * 会被当成没有标签而原样留下,归一化就白做了。
     */
    public static String titleKey(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }

        String text = unescapeHtml(title);
        text = HTML_TAG.matcher(text).replaceAll(" ");
        text = stripVersionSuffix(text);
        text = NON_ALPHANUMERIC.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    /**
     * 作者的比较键:取姓氏、去重、排序后拼起来。
     *
     * <p>只比姓氏,是因为正式版常补作者、调顺序,名字的缩写形式也会变 —— 比全名会漏掉一大片。
     *
     * <p><b>姓名格式的处理(保守):</b>
     * <ul>
     *   <li>`Li, Wei` → `li`(逗号前是姓)</li>
     *   <li>`Wei Li` → `li`(空格分隔时最后一段是姓)</li>
     *   <li>`李明` → `李明`(无分隔符时整段保留)</li>
     * </ul>
     * 最后一条是刻意的:中文姓名姓氏在前,按"最后一段"取会取到名,
     * 于是"李明"和"王明"会得到同一个键 —— 那是**错误的合并**。
     * 整段保留的代价是中文姓名在不同来源下写法不同时认不出来(漏合并),这个方向是安全的。
     */
    public static String authorKey(List<String> authors) {
        if (authors == null || authors.isEmpty()) {
            return "";
        }

        TreeSet<String> surnames = new TreeSet<>();
        for (String author : authors) {
            String surname = surnameOf(author);
            if (!surname.isEmpty()) {
                surnames.add(surname);
            }
        }
        return String.join("|", surnames);
    }

    /**
     * 两条记录是不是同一篇。
     *
     * <p>三个条件全中才算:**年份相近** + **标题相同** + **作者相同**。
     * 任一侧缺年份或缺作者都判为否 —— 宁可多一行,不可合错。
     */
    public static boolean matches(String titleA, List<String> authorsA, Integer yearA,
                                  String titleB, List<String> authorsB, Integer yearB) {
        if (yearA == null || yearB == null || Math.abs(yearA - yearB) > YEAR_TOLERANCE) {
            return false;
        }

        String keyA = titleKey(titleA);
        if (keyA.isEmpty() || !keyA.equals(titleKey(titleB))) {
            return false;
        }

        String authorsKeyA = authorKey(authorsA);
        String authorsKeyB = authorKey(authorsB);
        // 缺作者是"这条记录没写",不是"作者相同" —— 无从核对就不合并
        if (authorsKeyA.isEmpty() || authorsKeyB.isEmpty()) {
            return false;
        }
        return authorsKeyA.equals(authorsKeyB);
    }

    /**
     * 只凭标题认定时,标题至少要有几个词。
     *
     * <p>挡掉 "Editorial"、"Preface" 这类通用短标题 —— 它们重名是常有的事。
     */
    public static final int MIN_TITLE_WORDS_FOR_TITLE_ONLY = 4;

    /**
     * **反查专用**的退让规则:我们这边没有任何佐证时,标题完全一致就认。
     *
     * <p>标准规则({@link #matches})要求作者与年份佐证,而我们自己的记录**缺这两项**时
     * 它永远判否 —— 于是就算 arXiv 上有一篇标题一字不差的预印本,也读不了。
     * 实测(2026-10-01)确实存在这种记录:库里 166 篇里有 8 篇「无编号 + 无作者 + 无年份」,
     * 全部来自 Crossref,例如 "Polymer-Agent: Large Language Model Agent for Polymer Design" ——
     * arXiv 上有同名的 2601.16376,标准规则却认不出来。
     *
     * <p><b>退让只在"我们什么都拿不出"时生效</b>:一旦有作者或年份,就必须走标准规则核对。
     * 这是刻意的 —— 有佐证却不用,才是真正的隐患。
     *
     * <p><b>只给反查用,跨源合并不许用</b>:合并错了是把两篇论文并成一篇、收藏评分挂错地方,
     * 不可逆;反查错了只是把用户领到标题相同的另一篇全文上,代价小一个量级。
     */
    public static boolean matchesByTitleAlone(String titleA, List<String> authorsA, Integer yearA,
                                              String titleB, List<String> authorsB, Integer yearB) {
        if (yearA != null || !authorKey(authorsA).isEmpty()) {
            return false;
        }

        String keyA = titleKey(titleA);
        if (keyA.isEmpty() || !keyA.equals(titleKey(titleB))) {
            return false;
        }
        return keyA.split(" ").length >= MIN_TITLE_WORDS_FOR_TITLE_ONLY;
    }

    // ── 内部 ────────────────────────────────────────────────

    private static String surnameOf(String author) {
        if (author == null || author.isBlank()) {
            return "";
        }

        String text = unescapeHtml(author).trim();

        int comma = text.indexOf(',');
        if (comma > 0) {
            return normalizeWord(text.substring(0, comma));
        }

        String[] parts = text.split("\\s+");
        if (parts.length <= 1) {
            // 没有分隔符(中文姓名、或单名)—— 整段保留,不做猜测
            return normalizeWord(text);
        }
        return normalizeWord(parts[parts.length - 1]);
    }

    private static String normalizeWord(String word) {
        return NON_ALPHANUMERIC.matcher(word.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    /** 反复剥掉尾部的版本标记:可能叠着好几层,如 "(Extended Version) (v2)"。 */
    private static String stripVersionSuffix(String title) {
        String current = title;
        for (int round = 0; round < 3; round++) {
            String stripped = VERSION_SUFFIX.matcher(current).replaceFirst("");
            if (stripped.equals(current)) {
                break;
            }
            current = stripped;
        }
        return current;
    }

    /**
     * 还原常见的 HTML 转义。
     *
     * <p>不用完整的 HTML 解析器:论文标题里出现的就这么几种,而且这里只需要"够用且可预期"。
     */
    private static String unescapeHtml(String text) {
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&");
    }
}
