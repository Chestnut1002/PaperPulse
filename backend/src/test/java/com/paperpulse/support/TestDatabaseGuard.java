package com.paperpulse.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 防止集成测试连到开发库的护栏。
 *
 * <p><b>为什么时机是关键:</b>测试配置用的是 {@code ddl-auto: create-drop},它会在
 * Spring 容器启动时**删表重建**。如果等到 {@code @BeforeAll} / {@code @BeforeEach} 再去核对库名,
 * 开发库的表早就没了 —— 护栏必须跑在容器 refresh **之前**。{@link ApplicationContextInitializer}
 * 正好在这个时机执行:此时配置已经加载完(环境变量也在内),但还没有任何 Bean 被创建。
 *
 * <p>需要提防的是**优先级高于配置文件**的那些来源:环境变量 {@code SPRING_DATASOURCE_URL}、
 * 命令行 {@code -Dspring.datasource.url=...} 等。它们会悄悄盖掉 {@code application-test.yml}
 * 里写好的测试库地址。(顺带一提:{@code DB_URL} 不在其列 —— 它只是 {@code application.yml}
 * 里那个占位符的名字,而测试 profile 会把整个 url 属性替换掉,占位符根本不会求值。)
 */
public class TestDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /** 必须与 application-test.yml 中 JDBC URL 的库名一致。 */
    static final String EXPECTED_DATABASE = "paperpulse_test";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String url = context.getEnvironment().getProperty("spring.datasource.url", "");

        if (!url.contains("/" + EXPECTED_DATABASE)) {
            throw new IllegalStateException("""
                    集成测试中止:数据库地址不是测试库。

                      期望连到:%s
                      实际配置:%s

                    测试配置使用 ddl-auto: create-drop,启动时会删表重建,连错库会清空数据。
                    常见的覆盖来源(优先级均高于配置文件,请先清除):
                      - 环境变量 SPRING_DATASOURCE_URL
                      - 命令行参数 -Dspring.datasource.url=...
                    """.formatted(EXPECTED_DATABASE, url.isBlank() ? "(空)" : url));
        }
    }
}
