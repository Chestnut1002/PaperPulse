package com.paperpulse.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置项,对应 application.yml 中的 {@code jwt.*}。
 *
 * <p>record 形式由 Spring Boot 自动做构造器绑定;短横线命名会按宽松绑定规则映射到驼峰字段
 * ({@code expiration-seconds} → {@code expirationSeconds})。
 *
 * <p><b>密钥必填,校验放在构造器里</b> —— 绑定失败即终止启动。这里曾经有一个写死的默认值,
 * 它随着仓库公开了:签名密钥公开意味着任何人都能伪造登录凭证。默认值的问题不在于"值弱",
 * 而在于它把「忘记配置」变成「无声地用一个公开密钥运行」。校验把它变成启动错误 ——
 * 后者一眼可见、可修复,前者要到出事才知道。
 *
 * <p>密钥长度由 jjwt 在 {@link JwtService} 构造时校验(HS256 要求 ≥256 位),
 * 这里不重复实现。
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expirationSeconds) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("""
                    未配置 jwt.secret,应用拒绝启动。
                    注入方式:环境变量 JWT_SECRET,或本机私有的 application-local.yml。
                    仓库里不提供默认密钥 —— 默认密钥会随仓库公开,读过它的人就能伪造任意用户的登录凭证。
                    生成一个:`openssl rand -base64 48`""");
        }
    }
}
