package com.paperpulse.library;

import com.paperpulse.library.dto.FavoriteResponse;
import com.paperpulse.library.dto.HistoryResponse;
import com.paperpulse.library.dto.RatingRequest;
import com.paperpulse.library.dto.RatingResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收藏 / 阅读历史 / 评分接口(F6)。
 *
 * <p>三组接口都挂在 {@code /api/users/me} 下,当前用户一律来自 token,
 * **路径里不出现用户 id** —— 否则就得在每个方法里校验"路径上的 id 是不是你自己",
 * 一旦漏掉一处就是越权。
 */
@RestController
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    // ── 收藏 ───────────────────────────────────────────────

    @GetMapping("/api/users/me/favorites")
    public List<FavoriteResponse> favorites(@AuthenticationPrincipal Long userId) {
        return libraryService.listFavorites(userId);
    }

    /** 收藏一篇论文。幂等:重复调用返回同一条,不会报错也不会重复插入。 */
    @PostMapping("/api/users/me/favorites/{paperId}")
    public FavoriteResponse addFavorite(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long paperId) {
        return libraryService.addFavorite(userId, paperId);
    }

    /** 取消收藏。没收藏过返回 404,而不是假装成功。 */
    @DeleteMapping("/api/users/me/favorites/{paperId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@AuthenticationPrincipal Long userId,
                               @PathVariable Long paperId) {
        libraryService.removeFavorite(userId, paperId);
    }

    // ── 阅读历史 ───────────────────────────────────────────

    /**
     * 阅读历史,最近读的在前。
     *
     * @param limit 最多返回多少条,默认 {@value LibraryService#DEFAULT_HISTORY_LIMIT},
     *              上限 {@value LibraryService#MAX_HISTORY_LIMIT}
     */
    @GetMapping("/api/users/me/history")
    public List<HistoryResponse> history(@AuthenticationPrincipal Long userId,
                                         @RequestParam(defaultValue = "" + LibraryService.DEFAULT_HISTORY_LIMIT)
                                         int limit) {
        return libraryService.listHistory(userId, limit);
    }

    /** 记一次阅读。同一篇论文反复调用是累加次数,不是追加记录。 */
    @PostMapping("/api/users/me/history/{paperId}")
    public HistoryResponse recordRead(@AuthenticationPrincipal Long userId,
                                      @PathVariable Long paperId) {
        return libraryService.recordRead(userId, paperId);
    }

    /** 清空阅读历史。已经是空的也返回 204 —— 结果状态一致,没必要报错。 */
    @DeleteMapping("/api/users/me/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(@AuthenticationPrincipal Long userId) {
        libraryService.clearHistory(userId);
    }

    // ── 评分 ───────────────────────────────────────────────

    @GetMapping("/api/users/me/ratings")
    public List<RatingResponse> ratings(@AuthenticationPrincipal Long userId) {
        return libraryService.listRatings(userId);
    }

    /** 打分。已有评分则覆盖。用 PUT 而不是 POST:一人一篇只有一个分数,是替换语义。 */
    @PutMapping("/api/users/me/ratings/{paperId}")
    public RatingResponse rate(@AuthenticationPrincipal Long userId,
                               @PathVariable Long paperId,
                               @Valid @RequestBody RatingRequest request) {
        return libraryService.rate(userId, paperId, request.score());
    }

    /** 取消评分。没评过返回 404。 */
    @DeleteMapping("/api/users/me/ratings/{paperId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRating(@AuthenticationPrincipal Long userId,
                             @PathVariable Long paperId) {
        libraryService.removeRating(userId, paperId);
    }
}
