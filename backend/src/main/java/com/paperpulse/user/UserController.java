package com.paperpulse.user;

import com.paperpulse.user.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户相关接口(需登录)。
 *
 * <p>F5(兴趣标签)、F6(收藏 / 阅读历史 / 论文评分)也会挂在这一层,所以独立成
 * {@code /api/users} 而非塞进 {@code /api/auth}。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 返回当前登录用户。
     *
     * <p>{@code @AuthenticationPrincipal} 取的是 {@code SecurityContext} 里的 principal,
     * 也就是 {@code JwtAuthenticationFilter} 放进去的用户 id。
     *
     * <p>刻意<b>每次回查数据库</b>,而不是直接信任 token 里的 username:token 一旦签发就无法撤回,
     * 回查数据库意味着用户被删除后其 token 立即失效。签名有效 ≠ 用户仍然存在。
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        return UserResponse.from(userService.getById(userId));
    }
}
