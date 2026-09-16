package com.paperpulse.config;

import com.paperpulse.security.JwtAuthenticationFilter;
import com.paperpulse.security.JwtService;
import com.paperpulse.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置。
 *
 * <p>本服务是纯 REST + JWT:不使用 Cookie 会话,因此关闭 CSRF、关闭表单登录与 Basic 认证,
 * 未认证请求统一返回 401 而不是 302 跳转登录页。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // strength 默认 10,单次哈希约 50~100ms —— 对正常登录无感,却能让暴力破解慢到不可行
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 注册/登录本身必须匿名可访问,否则拿不到第一个 token(死锁)
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                // 这里刻意用 new 而不是把 JwtAuthenticationFilter 声明成 @Component:
                // Spring Boot 会把容器里所有 Filter 类型的 Bean 再往 Servlet 容器注册一次,
                // 那样它会在安全链之外多跑一遍。直接 new 传进去,只让安全链持有它。
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                // 用自定义入口而非 HttpStatusEntryPoint:后者只回空 body 的 401,
                // 与 GlobalExceptionHandler 的 JSON 错误格式对不上。
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint));
        return http.build();
    }
}
