package com.paperpulse.user;

import com.paperpulse.common.ApiException;
import com.paperpulse.user.dto.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户业务逻辑。Controller 只负责 HTTP,规则都在这里。 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
}
