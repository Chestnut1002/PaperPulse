package com.paperpulse.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用 JDK 自带的 {@code javax.crypto.Mac} 自行签 JWT。
 *
 * <p><b>刻意不用 jjwt</b> —— 应用代码就是用 jjwt 验签的。若测试也用它来造 token,
 * 等于用被验证的实现去验证它自己:jjwt 理解错规范时,两边会一起错,测试照样通过。
 * 这里独立走一遍 HMAC-SHA512,才能证明 token 格式真的符合 JWT 规范。
 *
 * <p>本类只用来造**服务端不会主动签发**的 token(已过期的、{@code sub} 指向不存在用户的),
 * 用于验证"签名有效 ≠ 应该放行"。
 */
public class TokenForger {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final SecretKeySpec key;
    private final ObjectMapper objectMapper;

    public TokenForger(String secret, ObjectMapper objectMapper) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        this.objectMapper = objectMapper;
    }

    /** 按给定声明签一个结构合法的 HS512 token。 */
    public String forge(long userId, String username, Instant issuedAt, Instant expiresAt) {
        String header = BASE64_URL.encodeToString(toJson(Map.of("alg", "HS512")));

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", String.valueOf(userId));
        claims.put("username", username);
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        String payload = BASE64_URL.encodeToString(toJson(claims));

        String signingInput = header + "." + payload;
        return signingInput + "." + signToBase64(signingInput);
    }

    /** 对 {@code 头部.载荷} 重新计算签名。用于独立复算服务端签发的 token。 */
    public String signToBase64(String signingInput) {
        try {
            // Mac 实例不是线程安全的,每次新建 —— 开销远小于一次 HTTP 往返,不值得缓存
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(key);
            return BASE64_URL.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("本机 JDK 不支持 HmacSHA512?", ex);
        }
    }

    /**
     * 把 JWT 某一段的<b>解码后字节</b>翻转一个 bit,再重新编码回 Base64URL。
     *
     * <p><b>不要图省事去改字符串的最后一个字符。</b>HS512 签名是 64 字节,Base64URL 编码后是
     * 86 个字符 = 516 位,而真实数据只有 512 位 —— 最后一个字符的低 4 位纯属填充,不参与解码。
     * 把末位字符换成高 2 位相同的另一个字符(如 {@code 'A'} ↔ {@code 'B'},索引 0 与 1),
     * 解码出来的字节一模一样,签名照样有效。这样一来"篡改"有时根本没篡改到,用例就变成了掷骰子,
     * 全看签名末位碰巧是什么字符。
     */
    public static String flipBit(String tokenPart) {
        byte[] decoded = Base64.getUrlDecoder().decode(tokenPart);
        decoded[0] ^= 0x01;
        return BASE64_URL.encodeToString(decoded);
    }

    /** 拆出 JWT 的三段:{头部, 载荷, 签名}。 */
    public static String[] split(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("不是一个三段式 JWT:" + token);
        }
        return parts;
    }

    /** 解开载荷。注意:JWT 前两段只是 Base64,**任何人都能解开看** —— 所以里面不能放敏感信息。 */
    public JsonNode decodePayload(String token) {
        return decodePart(token, 1);
    }

    /** 解开头部,用于核对 {@code alg} 声明。 */
    public JsonNode decodeHeader(String token) {
        return decodePart(token, 0);
    }

    private JsonNode decodePart(String token, int index) {
        return objectMapper.readTree(Base64.getUrlDecoder().decode(split(token)[index]));
    }

    private byte[] toJson(Object value) {
        return objectMapper.writeValueAsBytes(value);
    }
}
