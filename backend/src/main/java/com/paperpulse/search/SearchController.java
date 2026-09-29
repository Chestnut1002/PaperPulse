package com.paperpulse.search;

import com.paperpulse.search.dto.PaperSearchRequest;
import com.paperpulse.search.dto.PaperSearchResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自然语言检索论文(REQ-002)。
 *
 * <p>挂在 {@code /api/papers} 下而不是新开一个前缀:它产出的就是论文资源,
 * 与 {@code POST /api/papers} 是"同一类东西的不同获取方式"。
 */
@RestController
@RequestMapping("/api/papers")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 用自然语言检索论文,结果自动落库并带本地 id 返回。
     *
     * <p>拿到 id 之后,前端可以直接收藏 / 记阅读 / 打分,不必再走一次元数据提交。
     *
     * <p><b>失败语义:</b>上游 ai-service 出错返回 502,连不上返回 503 —— 都不是 500。
     * 把"依赖的服务挂了"和"我们的代码有 bug"在状态码上分开,排查方向完全不同。
     */
    @PostMapping("/search")
    public PaperSearchResponse search(@AuthenticationPrincipal Long userId,
                                      @Valid @RequestBody PaperSearchRequest request) {
        return searchService.search(userId, request);
    }
}
