package com.paperpulse.interest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 用户的一条兴趣(标签 + 权重)。
 *
 * <p><b>存的是 {@link InterestTag} 的 key 而不是展示名</b>:展示名以后可能改措辞,key 不会。
 * 反过来说,数据库里出现词表里已不存在的 key 是可能的(标签被下线),
 * 读取时按词表过滤掉即可 —— 见 {@code InterestService}。
 *
 * <p><b>用普通的 {@code userId} 列而不是 {@code @ManyToOne} 关联。</b>这里永远只按当前登录用户的 id
 * 查,不需要在兴趣行上再加载一个 User 代理对象;真要关联反而给每次读取都添一次无谓的查询。
 * 代价是没有数据库级外键 —— 当前没有"注销用户"功能,不存在孤儿行的来源;等真要做注销时,
 * 记得把清理逻辑一并写上。
 *
 * <p>唯一约束 {@code (user_id, tag_key)} 保证一个用户同一个标签只有一行 ——
 * 权重是"一条记录上的一个值",不是可以累加的事件。
 */
@Entity
@Table(name = "user_interest",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_interest_user_tag",
                columnNames = {"user_id", "tag_key"}))
public class UserInterest {

    /** 权重取值区间,前后端共用同一套定义。 */
    public static final int MIN_WEIGHT = 1;
    public static final int MAX_WEIGHT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** {@link InterestTag#key()}。长度留出余量,免得以后加长标签名要改表。 */
    @Column(name = "tag_key", nullable = false, length = 64)
    private String tagKey;

    /** 兴趣强度 1~5。范围校验在 {@code InterestService},这里是最后一道存储约束。 */
    @Column(nullable = false)
    private int weight;

    /** JPA 要求实体必须有公开或受保护的无参构造函数。 */
    protected UserInterest() {
    }

    public UserInterest(Long userId, String tagKey, int weight) {
        this.userId = userId;
        this.tagKey = tagKey;
        this.weight = weight;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTagKey() {
        return tagKey;
    }

    public int getWeight() {
        return weight;
    }
}
