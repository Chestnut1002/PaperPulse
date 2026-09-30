package com.paperpulse.reading;

import com.paperpulse.paper.dto.PaperResponse;
import com.paperpulse.reading.dto.ArxivOpenRequest;
import com.paperpulse.reading.dto.QaRequest;
import com.paperpulse.reading.dto.QaResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 论文精读问答(REQ-003)。
 *
 * <p>路径按论文 id 走,而不是 `/api/users/me/...` —— 问答的对象是**论文**,不是用户的数据。
 * 但接口仍需登录:它要调外部模型,不能对匿名者开放。
 */
@RestController
public class ReadingController {

    private final ReadingService readingService;

    public ReadingController(ReadingService readingService) {
        this.readingService = readingService;
    }

    /**
     * 就一篇论文提一个问题。
     *
     * <p><b>论文没有全文时返回 400 并说清原因</b>,而不是 404 或 500 ——
     * "这篇读不了"是请求的性质,不是"服务找不到东西"。
     */
    @PostMapping("/api/papers/{paperId}/qa")
    public QaResponse ask(@AuthenticationPrincipal Long userId,
                          @PathVariable Long paperId,
                          @Valid @RequestBody QaRequest request) {
        return readingService.ask(userId, paperId, request);
    }

    /**
     * 粘贴 arXiv 编号或链接,直接打开这篇论文。
     *
     * <p>精读是这个项目的主功能,而"手里已经有一篇论文想读它"是最自然的入口 ——
     * 不该要求用户先去搜一遍、还得指望它出现在结果里。
     *
     * <p>返回落库后的论文(带本地 id),前端拿它进精读页。
     */
    @PostMapping("/api/papers/from-arxiv")
    public PaperResponse openFromArxiv(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody ArxivOpenRequest request) {
        return readingService.openByArxiv(userId, request.reference());
    }
}
