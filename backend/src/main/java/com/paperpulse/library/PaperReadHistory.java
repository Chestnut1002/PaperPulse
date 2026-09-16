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
 * 一个用户读过某篇论文的记录(F6)。
 *
 * <p><b>记"最近一次 + 次数",不记事件流水。</b>推荐系统要的是"读过几次、多久以前读过",
 * 这两个数字从流水日志里也能算出来,但每次都要 {@code GROUP BY} —— 存成一行是同一份信息的
 * 更紧凑表示。真要做会话级分析(比如"连续读了三篇同一主题")时再补流水表。
 *
 * <p>唯一约束 {@code (user_id, paper_id)}:同一篇论文反复阅读是同一条记录上的计数增加,
 * 不是多条记录。
 *
 * <p><b>已知取舍:</b>{@code readCount} 的更新是"读出来 +1 再写回",两个请求同时记录同一篇
 * 论文的阅读会丢掉一次计数。这是**刻意接受**的 —— 阅读次数是弱信号,少算一次不影响推荐质量,
 * 而为了它引入原子自增语句要付出更多复杂度。相比之下,论文行撞唯一约束会让用户看到 500,
 * 所以那一处做了处理(见 {@code PaperService#resolve})。两者的严重程度不同,处理方式也就不同。
 */
@Entity
@Table(name = "paper_read_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_paper_read_user_paper",
                columnNames = {"user_id", "paper_id"}))
public class PaperReadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    /** 最近一次阅读的时间。列表按它倒序。 */
    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt;

    /** 累计阅读次数,从 1 开始。 */
    @Column(name = "read_count", nullable = false)
    private int readCount;

    /** JPA 要求实体必须有公开或受保护的无参构造函数。 */
    protected PaperReadHistory() {
    }

    public PaperReadHistory(Long userId, Long paperId, Instant readAt) {
        this.userId = userId;
        this.paperId = paperId;
        this.lastReadAt = readAt;
        this.readCount = 1;
    }

    /** 再读一次:次数加一,时间前移。 */
    public void recordAnotherRead(Instant readAt) {
        this.readCount++;
        this.lastReadAt = readAt;
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

    public Instant getLastReadAt() {
        return lastReadAt;
    }

    public int getReadCount() {
        return readCount;
    }
}
