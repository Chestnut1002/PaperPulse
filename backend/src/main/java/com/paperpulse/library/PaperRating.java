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
 * 一个用户给一篇论文打的分(F6)。
 *
 * <p>与收藏一样是"状态":一人一篇一个分数,改分是覆盖而不是追加。唯一约束
 * {@code (user_id, paper_id)} 保证这一点。
 *
 * <p><b>分数用 1~5 的整数</b>,与 F5 的兴趣权重同一套刻度 —— 用户不用学两套标准,
 * 前端可以直接渲染成星级。0 不是合法值:"不感兴趣"的正确表达是不打分、或取消收藏,
 * 让 0 也成为合法值会制造两种语义相同的表示。
 *
 * <p>分数区间常量放在这里,校验注解和用例共用同一处定义,不会出现"改了这里忘了那里"。
 */
@Entity
@Table(name = "paper_rating",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_paper_rating_user_paper",
                columnNames = {"user_id", "paper_id"}))
public class PaperRating {

    /** 评分区间,前后端共用。 */
    public static final int MIN_SCORE = 1;
    public static final int MAX_SCORE = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    /** 1~5。范围校验在服务层与 DTO,这里是最后一道存储约束。 */
    @Column(nullable = false)
    private int score;

    /** 最近一次打分的时间。改分时一并前移。 */
    @Column(name = "rated_at", nullable = false)
    private Instant ratedAt;

    /** JPA 要求实体必须有公开或受保护的无参构造函数。 */
    protected PaperRating() {
    }

    public PaperRating(Long userId, Long paperId, int score, Instant ratedAt) {
        this.userId = userId;
        this.paperId = paperId;
        this.score = score;
        this.ratedAt = ratedAt;
    }

    /** 改分。时间一并前移 —— 它表达的是"这个分数是什么时候给的"。 */
    public void changeScore(int newScore, Instant ratedAt) {
        this.score = newScore;
        this.ratedAt = ratedAt;
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

    public int getScore() {
        return score;
    }

    public Instant getRatedAt() {
        return ratedAt;
    }
}
