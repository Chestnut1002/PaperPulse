package com.paperpulse.user.dto;

import com.paperpulse.user.User;

import java.time.Instant;

/**
 * 对外返回的用户信息。
 *
 * <p>刻意与 {@link User} 实体分离:实体持有 password 字段,直接序列化实体会把密码哈希泄露给客户端。
 * 用专门的出参对象,是杜绝这类泄露最省心的办法。
 */
public record UserResponse(Long id, String username, String email, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt());
    }
}
