package com.paperpulse.user.dto;

/**
 * 登录成功的响应体。
 *
 * @param token      JWT,前端需在后续请求中以 {@code Authorization: Bearer <token>} 携带
 * @param tokenType  固定为 Bearer,遵循 RFC 6750
 * @param expiresIn  token 有效期(秒),前端可据此提前续期
 * @param user       登录用户信息(不含密码)
 */
public record LoginResponse(String token, String tokenType, long expiresIn, UserResponse user) {

    public static LoginResponse bearer(String token, long expiresIn, UserResponse user) {
        return new LoginResponse(token, "Bearer", expiresIn, user);
    }
}
