package com.paperpulse.reading;

import com.paperpulse.common.ApiException;
import com.paperpulse.paper.Paper;
import com.paperpulse.paper.PaperService;
import com.paperpulse.reading.dto.QaRequest;
import com.paperpulse.reading.dto.QaResponse;
import com.paperpulse.user.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 论文精读问答(REQ-003)。
 *
 * <p>本服务只管两件事:**确认这篇论文能不能精读**,以及**把请求转给 ai-service**。
 * 抓全文、切节、组装上下文都在 Python 侧 —— 那些是文本处理,放在那里比放在这里自然。
 */
@Service
public class ReadingService {

    private final PaperService paperService;
    private final AiReadingClient aiReadingClient;
    private final UserService userService;

    public ReadingService(PaperService paperService, AiReadingClient aiReadingClient,
                          UserService userService) {
        this.paperService = paperService;
        this.aiReadingClient = aiReadingClient;
        this.userService = userService;
    }

    public QaResponse ask(Long userId, Long paperId, QaRequest request) {
        // 回查用户:token 签名有效不等于用户仍然存在(与其余接口同一个落点)
        userService.getById(userId);

        Paper paper = paperService.require(paperId);
        String arxivId = paper.getArxivId();
        if (arxivId == null || arxivId.isBlank()) {
            // 说得具体一点:用户该知道是**这一篇**读不了,而不是服务坏了
            throw ApiException.badRequest(
                    "这篇论文没有可精读的全文 —— 目前只支持能从 arXiv 取到全文的论文");
        }

        return aiReadingClient.ask(arxivId, request.question().trim(), turnsOf(request));
    }

    private static List<Map<String, String>> turnsOf(QaRequest request) {
        return request.history().stream()
                .map(turn -> Map.of("role", turn.role(), "content", turn.content()))
                .toList();
    }
}
