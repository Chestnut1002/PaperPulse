package com.paperpulse.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求体。校验规则写在这里,Controller 加 {@code @Valid} 即自动生效。
 */
public record RegisterRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度需在 3~50 之间")
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
        String username,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱长度不能超过 100")
        String email,

        // 上限 72 来自 BCrypt 本身:它只取前 72 字节做哈希,超长部分会被静默忽略,
        // 与其让用户以为设了长密码,不如直接拒绝。(注意中文 1 字 = 3 字节)
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 72, message = "密码长度需在 6~72 之间")
        String password
) {
}
