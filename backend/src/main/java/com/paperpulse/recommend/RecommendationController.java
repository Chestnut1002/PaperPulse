package com.paperpulse.recommend;

import com.paperpulse.recommend.dto.RecommendationResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个性化推荐(REQ-004)。
 *
 * <p>挂在 {@code /api/users/me} 下,与收藏 / 历史 / 评分一致 —— 当前用户取自 token,
 * 路径里不出现用户 id。
 */
@RestController
@RequestMapping("/api/users/me")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /**
     * 按用户的兴趣标签推荐论文。
     *
     * @param limit          要几条(默认 10,上限 30)
     * @param diversityRatio 探索位比例 0~1(默认 0.3)。调大它,列表里"非主要方向"的论文就更多
     */
    @GetMapping("/recommendations")
    public RecommendationResponse recommend(@AuthenticationPrincipal Long userId,
                                            @RequestParam(required = false) Integer limit,
                                            @RequestParam(required = false) Double diversityRatio) {
        return recommendationService.recommend(userId, limit, diversityRatio);
    }
}
