package com.paperpulse.security;

import com.paperpulse.common.ApiException;
import com.paperpulse.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 的签发与校验。
 *
 * <p>JWT 由三部分组成:{@code 头部.载荷.签名},前两段只是 Base64 编码(**任何人都能解开看**),
 * 真正起作用的是第三段签名 —— 服务端用密钥对前两段做 HMAC,篡改任何一位都会导致签名对不上。
 * 因此 JWT 里<b>不能放敏感信息</b>,它保证的是"没被改过",不是"看不见"。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        // HS256 要求密钥至少 256 位(32 字节)。密钥过短时 jjwt 会直接抛异常,
        // 把弱密钥拦在启动期而不是等线上被人伪造 token —— 这是好事。
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofSeconds(properties.expirationSeconds());
    }

    /** 为用户签发 token。subject 存用户 id,并附带 username 以便前端直接展示。 */
    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /**
     * 校验签名与有效期,返回用户 id。
     *
     * <p>过期、签名不符、格式错误都归为同一类失败 —— 不向客户端区分原因,避免给攻击者提供线索。
     */
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            // IllegalArgumentException 一并捕获,涵盖 NumberFormatException(subject 非数字)
            throw ApiException.unauthorized("登录凭证无效或已过期");
        }
    }

    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }
}
