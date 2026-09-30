package com.paperpulse.reading;

import com.paperpulse.common.ApiException;
import com.paperpulse.paper.Paper;
import com.paperpulse.paper.PaperMatcher;
import com.paperpulse.paper.PaperService;
import com.paperpulse.paper.dto.PaperInput;
import com.paperpulse.paper.dto.PaperResponse;
import com.paperpulse.reading.dto.AiArxivLookupResponse;
import com.paperpulse.reading.dto.AiPaper;
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

    /**
     * 按用户粘贴的内容找到一篇论文,落库并返回。
     *
     * <p>用在"我手里有一篇论文想读"那条路上 —— 用户不该被迫先搜一遍、还得指望它出现在结果里。
     * 拿到之后前端直接进精读页。
     */
    public PaperResponse openByArxiv(Long userId, String rawReference) {
        userService.getById(userId);

        String arxivId = ArxivReference.extract(rawReference);
        if (arxivId == null) {
            throw ApiException.badRequest(
                    "没认出 arXiv 编号 —— 可以粘贴编号(如 2502.19271)或 arXiv 的链接");
        }

        AiArxivLookupResponse found = aiReadingClient.lookupArxiv(arxivId);
        if (!found.found() || found.paper() == null) {
            throw ApiException.notFound("arXiv 上找不到这个编号:" + arxivId);
        }

        AiPaper paper = found.paper();
        // 走既有的 resolve:跨来源合并会自动生效(这篇如果已经以别的身份在库里,不会多存一行)
        return PaperResponse.of(paperService.resolve(new PaperInput(
                paper.source(), paper.externalId(), paper.doi(), paper.arxivId(),
                paper.title(), paper.authors(), paper.abstractText(),
                paper.publicationYear(), paper.venue(), paper.url())));
    }

    /**
     * 给一篇没有 arXiv 编号的论文按标题反查预印本(REQ-003 P3)。**幂等**。
     *
     * <p>用在精读页的"读不了"面板上:查到就把编号补进这一行 —— 这篇从此能精读,
     * 而且**所有用户**都不必再查。查不到是常态(期刊论文未必有预印本),
     * 返回 404 并给一句人话,不当作服务出错。
     *
     * <p><b>认不认由这里决定,不交给 Python</b>:候选逐个过 {@link PaperMatcher}
     * (标题 + 作者 + 年份,三条件全中;我们自己的记录没有作者与年份时,退让到"标题完全一致
     * 且足够长")—— 认错的代价是把用户领到另一篇论文的全文上,那是不可逆的误导。
     */
    public PaperResponse resolveArxiv(Long userId, Long paperId) {
        userService.getById(userId);

        Paper paper = paperService.require(paperId);
        String arxivId = paper.getArxivId();
        if (arxivId != null && !arxivId.isBlank()) {
            // 已经有编号:直接返回,不麻烦上游 —— 这个接口本来就可能被重复点
            return PaperResponse.of(paper);
        }

        List<AiPaper> candidates = aiReadingClient.lookupArxivByTitle(paper.getTitle()).candidates();
        for (AiPaper candidate : candidates) {
            if (candidate.arxivId() == null || candidate.arxivId().isBlank()) {
                continue; // 没有编号的候选对"能不能精读"毫无帮助
            }
            if (isSamePaper(paper, candidate)) {
                return PaperResponse.of(paperService.attachArxivId(paperId, candidate.arxivId()));
            }
        }

        throw ApiException.notFound("arXiv 上没有找到这篇论文的预印本");
    }

    /**
     * 库里这篇与 arXiv 候选是不是同一篇。
     *
     * <p>先按标准规则(标题 + 作者 + 年份);再由 {@link PaperMatcher#matchesByTitleAlone}
     * 兜住"我们这边无作者、无年份"的记录 —— 这类记录(实测库里 8 篇,全来自 Crossref)
     * 在标准规则下永远判否,arXiv 上就算有标题一字不差的预印本也读不了。
     */
    private boolean isSamePaper(Paper paper, AiPaper candidate) {
        return PaperMatcher.matches(paper.getTitle(), paper.getAuthors(), paper.getPublicationYear(),
                candidate.title(), candidate.authors(), candidate.publicationYear())
                || PaperMatcher.matchesByTitleAlone(paper.getTitle(), paper.getAuthors(),
                paper.getPublicationYear(), candidate.title(), candidate.authors(),
                candidate.publicationYear());
    }

    private static List<Map<String, String>> turnsOf(QaRequest request) {
        return request.history().stream()
                .map(turn -> Map.of("role", turn.role(), "content", turn.content()))
                .toList();
    }
}
