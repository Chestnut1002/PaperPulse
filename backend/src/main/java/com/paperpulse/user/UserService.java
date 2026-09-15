package com.paperpulse.user;

import com.paperpulse.common.ApiException;
import com.paperpulse.user.dto.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 用户业务逻辑。Controller 只负责 HTTP,规则都在这里。 */
@Service
public class UserService {

    /** 用户名不存在与密码错误共用同一句提示,避免向攻击者确认"这个用户名是存在的"。 */
    private static final String LOGIN_FAILED_MESSAGE = "用户名或密码错误";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 一个固定口令的哈希,仅在"用户名不存在"时被比对一次。启动时算一次即可,不必每次登录都算。
     */
    private final String dummyPasswordHash;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("timing-equalization-dummy");
    }

    /**
     * 注册新用户。
     *
     * <p>返回的实体含 password 字段,调用方必须用 {@code UserResponse.from(...)} 转换后再返回给客户端。
     */
    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw ApiException.conflict("用户名已被占用");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("邮箱已被注册");
        }

        User user = new User(
                request.username(),
                request.email(),
                // BCrypt 是单向哈希,不可逆。DB 里存的是密文,即使库被拖走也无法直接还原密码。
                passwordEncoder.encode(request.password())
        );
        return userRepository.save(user);
    }

    /**
     * 校验用户名与密码,成功返回用户实体,失败抛 401。
     */
    @Transactional(readOnly = true)
    public User authenticate(String username, String password) {
        Optional<User> found = userRepository.findByUsername(username);

        if (found.isEmpty()) {
            // 关键:即使没有这个用户,也照样跑一次 BCrypt 比对。
            // 否则"用户不存在"会因为跳过哈希而明显更快返回,攻击者靠响应耗时就
            // 能把系统里有哪些用户名一个个试出来(时序侧信道)。
            passwordEncoder.matches(password, dummyPasswordHash);
            throw ApiException.unauthorized(LOGIN_FAILED_MESSAGE);
        }

        User user = found.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw ApiException.unauthorized(LOGIN_FAILED_MESSAGE);
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.unauthorized("登录凭证无效或已过期"));
    }
}
