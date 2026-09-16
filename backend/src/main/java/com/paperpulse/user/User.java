package com.paperpulse.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 用户实体(REQ-001)。
 *
 * <p>表名用 {@code users} 而非 {@code user} —— {@code USER} 是 MySQL 保留字,直接建表会报语法错误。
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户名。<b>大小写不敏感</b>。
     *
     * <p>这不是 Java 代码决定的,而是 MySQL 列的排序规则 {@code utf8mb4_unicode_ci}
     * 决定的({@code _ci} = case insensitive)—— {@code WHERE username = 'Alice'} 会命中
     * {@code 'alice'}。好处是注册查重与登录校验天然一致,不会出现两个只差大小写的账号。
     *
     * <p>若日后把列的排序规则改成 {@code _bin} / {@code _cs},这里的行为会静默反转。
     * 改之前先看这条注释,并同步调整前端提示。
     */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** 邮箱。同样受 {@code utf8mb4_unicode_ci} 影响,大小写不敏感。 */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /** BCrypt 密文。全项目任何位置都不得存放或返回明文密码。 */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** JPA 要求实体必须有公开或受保护的无参构造函数。 */
    protected User() {
    }

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
