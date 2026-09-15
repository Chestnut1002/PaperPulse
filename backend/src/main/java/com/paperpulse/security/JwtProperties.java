package com.paperpulse.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置项,对应 application.yml 中的 {@code jwt.*}。
 *
 * <p>record 形式由 Spring Boot 自动做构造器绑定;短横线命名会按宽松绑定规则映射到驼峰字段
 * ({@code expiration-seconds} → {@code expirationSeconds})。
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expirationSeconds) {
}
