package com.paperpulse.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 配置的启动期校验(纯逻辑,不起 Spring)。
 *
 * <p>这条校验的意义在于:密钥缺失时**必须让应用起不来**。此前的写法是给一个写死的默认值,
 * 于是"忘了配置"和"配置好了"在运行期表现完全一样 —— 唯一的差别是前者用着一个已经公开的密钥。
 * 所以这里测的重点是"缺了就拒绝",不是"值够不够强"(长度由 jjwt 在 JwtService 里管)。
 */
class JwtPropertiesTest {

    @Nested
    @DisplayName("密钥缺失时拒绝启动")
    class MissingSecret {

        @Test
        void null_被拒绝() {
            assertThatThrownBy(() -> new JwtProperties(null, 86400))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jwt.secret");
        }

        @Test
        void 空串被拒绝() {
            assertThatThrownBy(() -> new JwtProperties("", 86400))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void 全空白被拒绝() {
            // 只判断 null 不够:`.env` 里写了 `JWT_SECRET=` 得到的是空串而不是 null,
            // 环境变量设成空格同样是"配了等于没配"
            assertThatThrownBy(() -> new JwtProperties("   \t\n ", 86400))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void 报错信息要写清注入方式() {
            // 只说"没配"没用 —— 读到这条消息的人需要立刻知道"那该怎么配"
            assertThatThrownBy(() -> new JwtProperties("", 86400))
                    .hasMessageContaining("JWT_SECRET")
                    .hasMessageContaining("application-local.yml");
        }
    }

    @Nested
    @DisplayName("正常配置")
    class Valid {

        @Test
        void 有密钥就通过() {
            JwtProperties properties = new JwtProperties("a".repeat(48), 86400);

            assertThat(properties.secret()).hasSize(48);
            assertThat(properties.expirationSeconds()).isEqualTo(86400);
        }
    }
}
