package com.paperpulse.reading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 从用户粘贴的内容里抽 arXiv 编号(纯逻辑)。
 *
 * <p>用户手上有什么就贴什么 —— 链接、编号、带版本号的都可能。让他自己抠编号是不合理的,
 * 而这段解析的输入形态很多,所以测得比别处细一点。
 */
class ArxivReferenceTest {

    @Nested
    @DisplayName("认得出来")
    class Accepted {

        @Test
        void 裸编号() {
            assertThat(ArxivReference.extract("2502.19271")).isEqualTo("2502.19271");
        }

        @Test
        void 带首尾空白() {
            assertThat(ArxivReference.extract("  2502.19271  ")).isEqualTo("2502.19271");
        }

        @Test
        void 从_abs_链接里抽() {
            assertThat(ArxivReference.extract("https://arxiv.org/abs/2502.19271"))
                    .isEqualTo("2502.19271");
        }

        @Test
        void 从_pdf_链接里抽() {
            assertThat(ArxivReference.extract("https://arxiv.org/pdf/2502.19271.pdf"))
                    .isEqualTo("2502.19271");
        }

        @Test
        void 去掉版本号() {
            // 2502.19271v2 与 2502.19271 是同一篇的不同修订
            assertThat(ArxivReference.extract("2502.19271v2")).isEqualTo("2502.19271");
            assertThat(ArxivReference.extract("https://arxiv.org/abs/2502.19271v3"))
                    .isEqualTo("2502.19271");
        }

        @Test
        void 老式编号() {
            assertThat(ArxivReference.extract("cs/0701001")).isEqualTo("cs/0701001");
            assertThat(ArxivReference.extract("math.GT/0309136")).isEqualTo("math.gt/0309136");
        }

        @Test
        void 大小写不影响() {
            assertThat(ArxivReference.extract("ARXIV.ORG/ABS/2502.19271")).isEqualTo("2502.19271");
        }

        @Test
        void 一整句里夹着编号也认得出() {
            assertThat(ArxivReference.extract("读这篇 arXiv:2502.19271 谢谢"))
                    .isEqualTo("2502.19271");
        }
    }

    @Nested
    @DisplayName("认不出来就返回 null,而不是猜")
    class Rejected {

        @Test
        void 空值() {
            assertThat(ArxivReference.extract(null)).isNull();
            assertThat(ArxivReference.extract("   ")).isNull();
        }

        @Test
        void 普通网址不是_arXiv_编号() {
            assertThat(ArxivReference.extract("https://example.com/paper/12345")).isNull();
            assertThat(ArxivReference.extract("https://doi.org/10.1145/3640457.3688186")).isNull();
        }

        @Test
        void 斜杠前没有分类前缀的不算老式编号() {
            // "and/or" 这种常见写法不该被当成编号
            assertThat(ArxivReference.extract("cats and/or dogs")).isNull();
        }

        @Test
        void 数字太短的不算() {
            assertThat(ArxivReference.extract("2024")).isNull();
            assertThat(ArxivReference.extract("123")).isNull();
        }
    }
}
