package com.paperpulse.user;

import com.paperpulse.security.JwtService;
import com.paperpulse.user.dto.LoginRequest;
import com.paperpulse.user.dto.LoginResponse;
import com.paperpulse.user.dto.RegisterRequest;
import com.paperpulse.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 认证相关接口:注册、登录。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    /** 注册成功返回 201 Created + 用户信息(不含密码)。 */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(userService.register(request));
    }

    /**
     * 登录成功返回 200 + JWT。
     *
     * <p>凭据错误返回 401,且不区分是用户名不存在还是密码不对。
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(request.username(), request.password());
        String token = jwtService.generateToken(user);
        return LoginResponse.bearer(token, jwtService.getExpirationSeconds(), UserResponse.from(user));
    }
}
