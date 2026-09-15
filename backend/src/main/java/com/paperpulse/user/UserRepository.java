package com.paperpulse.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 用户仓储。方法名由 Spring Data 解析为查询,无需手写 SQL。 */
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);
}
