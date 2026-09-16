package com.paperpulse.library;

import com.paperpulse.common.ApiException;
import com.paperpulse.library.dto.FavoriteResponse;
import com.paperpulse.library.dto.HistoryResponse;
import com.paperpulse.library.dto.RatingResponse;
import com.paperpulse.paper.Paper;
import com.paperpulse.paper.PaperService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 收藏 / 阅读历史 / 评分(F6)。
 *
 * <p>三者都是"用户 × 论文"的关联数据,但**语义分成两类**,这直接决定了各自的写法:
 * <ul>
 *   <li><b>收藏、评分是状态</b> —— 一人一篇只有一条。重复收藏是幂等的(返回已有的那条),
 *       改分是覆盖。</li>
 *   <li><b>阅读是可重复的行为</b> —— 同一篇读两次是计数加一,不是两条记录。</li>
 * </ul>
 *
 * <p>三者互相独立,不互相引用:收藏列表不会带上评分。前端同时需要两者时取两个接口,
 * 在客户端按 paperId 合并 —— 比在后端做三路 join 简单,而且每个接口的返回形状保持单一。
 */
@Service
public class LibraryService {

    /** 不带 limit 时返回多少条历史。 */
    public static final int DEFAULT_HISTORY_LIMIT = 100;

    /**
     * 历史上限。
     *
     * <p>需要有个上限是因为历史会随时间无限增长,而"最近读了什么"只要开头一段。
     * 500 条足够覆盖任何真实使用,同时挡住一次请求把几年的记录全拉出来。
     */
    public static final int MAX_HISTORY_LIMIT = 500;

    private final PaperFavoriteRepository favorites;
    private final PaperReadHistoryRepository history;
    private final PaperRatingRepository ratings;
    private final PaperService paperService;

    public LibraryService(PaperFavoriteRepository favorites,
                          PaperReadHistoryRepository history,
                          PaperRatingRepository ratings,
                          PaperService paperService) {
        this.favorites = favorites;
        this.history = history;
        this.ratings = ratings;
        this.paperService = paperService;
    }

    // ── 收藏 ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FavoriteResponse> listFavorites(Long userId) {
        List<PaperFavorite> rows = favorites.findByUserIdOrderByFavoritedAtDesc(userId);
        Map<Long, Paper> papers = paperService.byIds(rows.stream().map(PaperFavorite::getPaperId).toList());

        return rows.stream()
                .filter(row -> papers.containsKey(row.getPaperId()))
                .map(row -> FavoriteResponse.of(papers.get(row.getPaperId()), row))
                .toList();
    }

    /**
     * 收藏一篇论文。**幂等** —— 已经收藏过就返回已有的那条,不报错也不重复插入。
     *
     * <p>这样前端双击、或者网络重试,都不会产生第二条记录,用户也不必先查一遍再决定调哪个接口。
     */
    @Transactional
    public FavoriteResponse addFavorite(Long userId, Long paperId) {
        Paper paper = paperService.require(paperId);

        PaperFavorite favorite = favorites.findByUserIdAndPaperId(userId, paperId)
                .orElseGet(() -> favorites.save(new PaperFavorite(userId, paperId, Instant.now())));

        return FavoriteResponse.of(paper, favorite);
    }

    @Transactional
    public void removeFavorite(Long userId, Long paperId) {
        // 0 行表示本来就没收藏。不能假装成功:前端拿着过期的界面点删除,
        // 回一个成功会让它以为删掉了,下次刷新又冒出来。
        if (favorites.deleteOwned(userId, paperId) == 0) {
            throw ApiException.notFound("没有收藏过这篇论文");
        }
    }

    // ── 阅读历史 ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<HistoryResponse> listHistory(Long userId, int limit) {
        List<PaperReadHistory> rows = history.findByUserIdOrderByLastReadAtDesc(
                userId, PageRequest.of(0, clampLimit(limit)));
        Map<Long, Paper> papers = paperService.byIds(rows.stream().map(PaperReadHistory::getPaperId).toList());

        return rows.stream()
                .filter(row -> papers.containsKey(row.getPaperId()))
                .map(row -> HistoryResponse.of(papers.get(row.getPaperId()), row))
                .toList();
    }

    /**
     * 记一次阅读。
     *
     * <p>同一篇论文反复调用是**累加次数**,不是追加记录 —— 见类注释。
     */
    @Transactional
    public HistoryResponse recordRead(Long userId, Long paperId) {
        Paper paper = paperService.require(paperId);
        Instant now = Instant.now();

        PaperReadHistory row = history.findByUserIdAndPaperId(userId, paperId)
                .map(existing -> {
                    existing.recordAnotherRead(now);
                    return history.save(existing);
                })
                .orElseGet(() -> history.save(new PaperReadHistory(userId, paperId, now)));

        return HistoryResponse.of(paper, row);
    }

    /** 清空阅读历史。**幂等** —— 本来就是空的也算成功,因为结果状态一致。 */
    @Transactional
    public void clearHistory(Long userId) {
        history.deleteAllOwned(userId);
    }

    // ── 评分 ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RatingResponse> listRatings(Long userId) {
        List<PaperRating> rows = ratings.findByUserIdOrderByRatedAtDesc(userId);
        Map<Long, Paper> papers = paperService.byIds(rows.stream().map(PaperRating::getPaperId).toList());

        return rows.stream()
                .filter(row -> papers.containsKey(row.getPaperId()))
                .map(row -> RatingResponse.of(papers.get(row.getPaperId()), row))
                .toList();
    }

    /** 打分。已有评分则**覆盖**,不是追加一条。 */
    @Transactional
    public RatingResponse rate(Long userId, Long paperId, int score) {
        Paper paper = paperService.require(paperId);
        Instant now = Instant.now();

        PaperRating row = ratings.findByUserIdAndPaperId(userId, paperId)
                .map(existing -> {
                    existing.changeScore(score, now);
                    return ratings.save(existing);
                })
                .orElseGet(() -> ratings.save(new PaperRating(userId, paperId, score, now)));

        return RatingResponse.of(paper, row);
    }

    @Transactional
    public void removeRating(Long userId, Long paperId) {
        if (ratings.deleteOwned(userId, paperId) == 0) {
            throw ApiException.notFound("没有给这篇论文打过分");
        }
    }

    // ── 内部 ───────────────────────────────────────────────

    /** 把 limit 收进合法区间。给 0 或负数时退回默认值,而不是返回空列表 —— 那不是调用方想要的。 */
    private static int clampLimit(int limit) {
        if (limit < 1) {
            return DEFAULT_HISTORY_LIMIT;
        }
        return Math.min(limit, MAX_HISTORY_LIMIT);
    }
}
