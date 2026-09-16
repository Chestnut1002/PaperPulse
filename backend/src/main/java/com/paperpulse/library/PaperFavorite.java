package com.paperpulse.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 一个用户收藏了一篇论文(F6)。
 *
 * <p><b>表达的是"状态"而不是"事件":</b>唯一约束 {@code (user_id, paper_id)} 保证一人一篇只有一行,
 * 重复收藏不是"收藏了两次",而是"本来就是收藏状态"。因此没有次数、没有流水 ——
 * 取消收藏就是删掉这一行。
 *
 * <p>与 {@link PaperReadHistory} 的区别正在于此:收藏是一次性的动作,阅读是可重复发生的行为。
 *
 * <p>用普通的 {@code userId} / {@code paperId} 列而不是 {@code @ManyToOne},
 * 理由同 F5 的 {@code UserInterest}:这里永远只按 id 查,不需要在收藏行上再加载 User 或 Paper 代理。
 * 列表接口会一次性批查出所有论文,不会产生 N+1。
 */
@Entity
@Table(name = "paper_favorite",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_paper_favorite_user_paper",
                columnNames = {"user_id", "paper_id"}))
public class PaperFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    /** 收藏时间。列表按它倒序,把最近收藏的排在前面。 */
    @Column(name = "favorited_at", nullable = false)
    private Instant favoritedAt;

    /** JPA 要求实体必须有公开或受保护的无参构造函数。 */
    protected PaperFavorite() {
    }

    public PaperFavorite(Long userId, Long paperId, Instant favoritedAt) {
        this.userId = userId;
        this.paperId = paperId;
        this.favoritedAt = favoritedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getPaperId() {
        return paperId;
    }

    public Instant getFavoritedAt() {
        return favoritedAt;
    }
}
