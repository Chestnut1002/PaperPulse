package com.paperpulse.interest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 用户兴趣仓储。 */
public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {

    List<UserInterest> findByUserId(Long userId);

    /**
     * 清空某用户的全部兴趣,供"全量替换"先删后插。
     *
     * <p><b>为什么显式写 JPQL 而不是用派生的 {@code deleteByUserId}:</b>派生删除走的是
     * "查出实体 → 逐个标记删除",而 Hibernate 刷盘时**插入先于删除**执行。
     * 于是"标签没变、只是权重变了"这种场景会先插新行、再删旧行,
     * 撞上 {@code (user_id, tag_key)} 唯一约束直接报错。
     * JPQL 的批量删除是**当场执行**的,不存在这个次序问题。
     *
     * <p>这不是推理,是实测过的:把本方法换成派生的 {@code deleteByUserId} 后,
     * 三项用例当场失败并报 {@code Duplicate entry '3-recommender_system' for key
     * 'user_interest.uk_user_interest_user_tag'}。
     */
    @Modifying
    @Query("delete from UserInterest u where u.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
