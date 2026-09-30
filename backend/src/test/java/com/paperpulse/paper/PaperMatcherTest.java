package com.paperpulse.paper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 跨源合并规则(纯逻辑,不起 Spring)。
 *
 * <p>这层是"判错了会静默损坏数据"的地方,所以测得比别处细 ——
 * 尤其是**不该合并的那些情况**。
 */
class PaperMatcherTest {

    @Nested
    @DisplayName("标题归一化")
    class TitleKey {

        @Test
        void 大小写与标点不影响() {
            assertThat(PaperMatcher.titleKey("Contrastive Learning: A Survey!"))
                    .isEqualTo(PaperMatcher.titleKey("contrastive  learning a survey"));
        }

        @Test
        void 去掉_HTML_标签() {
            assertThat(PaperMatcher.titleKey("<i>SiReN</i>: Sign-Aware Recommendation"))
                    .isEqualTo(PaperMatcher.titleKey("SiReN: Sign-Aware Recommendation"));
        }

        @Test
        void 还原_HTML_转义() {
            assertThat(PaperMatcher.titleKey("Models &amp; iContracts"))
                    .isEqualTo(PaperMatcher.titleKey("Models & iContracts"));
        }

        @Test
        void 先还原转义再去标签() {
            // 顺序反过来就白做了:&lt;i&gt; 会因为没有 "<" 而被当成没标签原样留下
            assertThat(PaperMatcher.titleKey("&lt;i&gt;SiReN&lt;/i&gt;: A Survey"))
                    .isEqualTo(PaperMatcher.titleKey("SiReN: A Survey"));
        }

        @Test
        void 剥掉版本后缀_实测漏网的主要原因() {
            assertThat(PaperMatcher.titleKey("IGNiteR: News Recommendation (Extended Version)"))
                    .isEqualTo(PaperMatcher.titleKey("IGNiteR: News Recommendation"));
            assertThat(PaperMatcher.titleKey("Some Paper (Preprint)"))
                    .isEqualTo(PaperMatcher.titleKey("Some Paper"));
            assertThat(PaperMatcher.titleKey("Some Paper [v2]"))
                    .isEqualTo(PaperMatcher.titleKey("Some Paper"));
            assertThat(PaperMatcher.titleKey("Some Paper (Version 3)"))
                    .isEqualTo(PaperMatcher.titleKey("Some Paper"));
        }

        @Test
        void 叠着好几层后缀也能剥干净() {
            assertThat(PaperMatcher.titleKey("Some Paper (Extended Version) (v2)"))
                    .isEqualTo(PaperMatcher.titleKey("Some Paper"));
        }

        @Test
        void 标题自带的括号不能误删() {
            // "BERT (Bidirectional …)" 里的括号是标题的一部分,删了就把两篇不同的论文混一起
            String title = "BERT (Bidirectional Encoder Representations from Transformers)";
            assertThat(PaperMatcher.titleKey(title)).contains("bidirectional").contains("transformers");
        }

        @Test
        void 空值与空白() {
            assertThat(PaperMatcher.titleKey(null)).isEmpty();
            assertThat(PaperMatcher.titleKey("   ")).isEmpty();
        }

        @Test
        void 换行被压缩() {
            assertThat(PaperMatcher.titleKey("Multi-view\n  Contrastive   Learning"))
                    .isEqualTo("multi view contrastive learning");
        }
    }

    @Nested
    @DisplayName("作者键")
    class AuthorKey {

        @Test
        void 顺序不同不算不同() {
            assertThat(PaperMatcher.authorKey(List.of("Wei Li", "Bo Wang")))
                    .isEqualTo(PaperMatcher.authorKey(List.of("Bo Wang", "Wei Li")));
        }

        @Test
        void 逗号形式取逗号前的姓() {
            assertThat(PaperMatcher.authorKey(List.of("Li, Wei"))).isEqualTo("li");
        }

        @Test
        void 空格形式取最后一段() {
            assertThat(PaperMatcher.authorKey(List.of("Wei Li"))).isEqualTo("li");
        }

        @Test
        void 中文姓名整段保留_按最后一段取会张冠李戴() {
            // "李明" 与 "王明" 若都取最后一段会得到同一个键 "明" —— 那是错误的合并
            assertThat(PaperMatcher.authorKey(List.of("李明"))).isNotEqualTo(PaperMatcher.authorKey(List.of("王明")));
        }

        @Test
        void 重复作者去重() {
            assertThat(PaperMatcher.authorKey(List.of("Wei Li", "Wei Li"))).isEqualTo("li");
        }

        @Test
        void 空列表() {
            assertThat(PaperMatcher.authorKey(List.of())).isEmpty();
            assertThat(PaperMatcher.authorKey(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("是否同一篇")
    class Matches {

        private final String title = "Contrastive Learning for Recommendation";
        private final List<String> authors = List.of("Wei Li", "Bo Wang");
        private final Integer year = 2024;

        @Test
        void 三条全中才算() {
            assertThat(PaperMatcher.matches(title, authors, year, title, authors, year)).isTrue();
        }

        @Test
        void 标题不同就不合并() {
            assertThat(PaperMatcher.matches(title, authors, year,
                    "Another Title Entirely", authors, year)).isFalse();
        }

        @Test
        void 作者不同就不合并() {
            // 实测 60 篇里有 1 篇标题完全相同但作者不同 —— 只看标题就会合错
            assertThat(PaperMatcher.matches(title, authors, year,
                    title, List.of("Someone Else"), year)).isFalse();
        }

        @Test
        void 年份差超过容忍度就不合并() {
            assertThat(PaperMatcher.matches(title, authors, 2024, title, authors, 2015)).isFalse();
        }

        @Test
        void 容忍度之内可以合并() {
            assertThat(PaperMatcher.matches(title, authors, 2024, title, authors, 2022)).isTrue();
        }

        @Test
        void 任一侧缺年份就不合并() {
            // 缺了年份,"同名同作者但不同时期"这条防线就没了 —— 宁可多一行
            assertThat(PaperMatcher.matches(title, authors, null, title, authors, 2024)).isFalse();
            assertThat(PaperMatcher.matches(title, authors, 2024, title, authors, null)).isFalse();
        }

        @Test
        void 任一侧缺作者就不合并() {
            // 缺作者是"这条记录没写",不是"作者相同"
            assertThat(PaperMatcher.matches(title, List.of(), year, title, authors, year)).isFalse();
            assertThat(PaperMatcher.matches(title, authors, year, title, List.of(), year)).isFalse();
        }

        @Test
        void 版本后缀造成的差异仍然算同一篇() {
            // 这是实测里最常见的真实情况:arXiv 版带后缀,正式版不带
            assertThat(PaperMatcher.matches(
                    "IGNiteR: News Recommendation (Extended Version)", authors, 2023,
                    "IGNiteR: News Recommendation", authors, 2023)).isTrue();
        }

        @Test
        void HTML_标签造成的差异仍然算同一篇() {
            assertThat(PaperMatcher.matches(
                    "<i>SiReN</i>: Sign-Aware Recommendation", authors, 2024,
                    "SiReN: Sign-Aware Recommendation", authors, 2024)).isTrue();
        }

        @Test
        void 标题为空时不合并() {
            assertThat(PaperMatcher.matches(null, authors, year, null, authors, year)).isFalse();
        }
    }
}
