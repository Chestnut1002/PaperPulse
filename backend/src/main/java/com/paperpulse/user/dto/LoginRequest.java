package com.paperpulse.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求体。
 *
 * <p>刻意<b>不加</b>长度与格式约束:注册规则日后可能调整,老用户的密码不能因此登不进来;
 * 而且响应里透露"密码长度不符合规则"等于向攻击者泄露了密码策略。
 */
public record LoginRequest(

        @NotBlank(message = "用户名不能为空")
        String username,

        @NotBlank(message = "密码不能为空")
        String password
) {
}
