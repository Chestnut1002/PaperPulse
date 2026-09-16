package com.paperpulse.security;

import com.paperpulse.common.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 从请求头解析 JWT,认证通过后把用户 id 写入 {@link SecurityContextHolder}。
 *
 * <p>继承 {@code OncePerRequestFilter} 而不是直接实现 {@code Filter}:Servlet 容器对
 * forward / include / 异步分发会把同一次请求再走一遍过滤器,而"我是谁"这件事每请求确认一次就够了。
 *
 * <p><b>本类刻意不做两件事</b>,都是为了让职责边界干净:
 * <ol>
 *   <li><b>不写 401 响应。</b>token 缺失或无效时仅仅"不认证",然后放行,由后面的授权规则决定拦不拦。
 *       若在这里直接写响应,401 就有了两个出口、两种响应格式,前端得写两套解析逻辑。</li>
 *   <li><b>不查数据库。</b>只把 userId 放进认证信息,需要用户实体时由业务层再查。
 *       否则每个受保护请求都要白跑一次 SQL,哪怕接口根本用不到用户行。</li>
 * </ol>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Long userId = jwtService.parseUserId(token);

                // 用 userId 作为 principal。第二个参数是密码,token 认证场景下没有密码,传 null。
                // 第三个参数是权限列表,REQ-001 还没有角色概念,给空列表。
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ApiException ex) {
                // token 存在但无效(过期 / 签名不符 / 格式错)。这里不抛也不写响应:
                // 上下文保持未认证,后面 AuthorizationFilter 会统一返回 401。
                // 用 DEBUG 而非 WARN —— 否则恶意请求可以用无效 token 刷爆日志。
                log.debug("JWT 校验失败:{}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 按 RFC 6750 解析 {@code Authorization: Bearer <token>}。
     *
     * <p>scheme 用不区分大小写的比较:RFC 7235 §2.1 规定认证 scheme 是大小写不敏感的,
     * {@code bearer}、{@code BEARER} 都应当被接受。用 {@code startsWith} 会把它们误判成格式错误。
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || header.length() <= BEARER_PREFIX.length()) {
            return null;
        }
        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
