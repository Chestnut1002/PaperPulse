package com.paperpulse.search;

import com.paperpulse.paper.PaperService;
import com.paperpulse.paper.dto.PaperInput;
import com.paperpulse.paper.dto.PaperResponse;
import com.paperpulse.search.dto.AiSearchResponse;
import com.paperpulse.search.dto.PaperSearchRequest;
import com.paperpulse.search.dto.PaperSearchResponse;
import com.paperpulse.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 检索编排:把 ai-service 找到的论文落库,再返回带本地 id 的结果。
 *
 * <p><b>为什么由后端落库,而不是把元数据回传给前端再让前端提交:</b>论文的元数据必须来自
 * 真正抓取它的那一方。若允许客户端提交任意元数据,一个登录用户就能抢占某个
 * {@code (source, externalId)} 并写入错误的标题 —— F6 留下的那条技术债正是这个,
 * 检索落地后这条路径就被堵上了。
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    /** 与 ai-service 的 DEFAULT_LIMIT 保持一致;请求里不传时用它。 */
    private static final int DEFAULT_LIMIT = 5;

    private final AiSearchClient aiSearchClient;
    private final PaperService paperService;
    private final UserService userService;

    public SearchService(AiSearchClient aiSearchClient, PaperService paperService, UserService userService) {
        this.aiSearchClient = aiSearchClient;
        this.paperService = paperService;
        this.userService = userService;
    }

    public PaperSearchResponse search(Long userId, PaperSearchRequest request) {
        // 回查用户,尽管检索本身用不到用户实体。
        //
        // 鉴权过滤器只验签、不查库(见 JwtAuthenticationFilter 的说明),所以"签名有效"只证明
        // token 是我们签发的,**不证明这个用户现在还在**。用户被删除后其 token 要到过期才失效 ——
        // 这条保证要么对所有接口成立,要么等于没有。UserService.getById 查不到会抛 401,
        // 与 GET /api/users/me 是同一个落点。
        userService.getById(userId);

        int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();

        AiSearchResponse found = aiSearchClient.search(request.query(), limit);
        AiSearchResponse.Plan plan = found.plan();

        List<PaperResponse> papers = dedupeById(papersOf(found).stream()
                .filter(SearchService::isComplete)
                .map(this::persist)
                .toList());

        return new PaperSearchResponse(
                found.query(),
                plan == null ? "" : Objects.toString(plan.keywords(), ""),
                plan == null ? "" : Objects.toString(plan.rationale(), ""),
                found.sourceLabel(),
                papers);
    }

    /**
     * 按**落库后的本地 id** 去重。
     *
     * <p>为什么在上游去过重了这里还要再做一次:上游只能按 `(来源, 外部 ID)` 和 DOI 去重,
     * 而这两条都不足以判定"同一篇"。真正的判定发生在落库时 —— 那里的跨源合并会认出
     * "标题、作者、年份都相同"的两个身份其实是同一篇,并把它们并到同一行。
     *
     * <p>不这样做的话,用户会在一次检索结果里看到同一篇论文出现两次
     * (实测 S2 就会返回两条外部 ID 不同、内容相同的记录)。
     */
    private static List<PaperResponse> dedupeById(List<PaperResponse> papers) {
        Map<Long, PaperResponse> unique = new LinkedHashMap<>();
        for (PaperResponse paper : papers) {
            unique.putIfAbsent(paper.id(), paper);
        }
        return List.copyOf(unique.values());
    }

    private static List<AiSearchResponse.Paper> papersOf(AiSearchResponse found) {
        return found.papers() == null ? List.of() : found.papers();
    }

    /**
     * 只接受元数据齐全的条目。
     *
     * <p>{@code source / externalId / title} 三个字段 {@code paper} 表都是非空约束,
     * 缺任一都会在写库时抛异常 —— 那会让**整次检索**失败。这里提前挡掉不完整的条目,
     * 一条坏数据不该毁掉其余十九条。
     */
    private static boolean isComplete(AiSearchResponse.Paper paper) {
        boolean complete = isPresent(paper.source())
                && isPresent(paper.externalId())
                && isPresent(paper.title());

        if (!complete) {
            log.warn("检索结果元数据不全,已跳过:source={} externalId={} title={}",
                    paper.source(), paper.externalId(), paper.title());
        }
        return complete;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 落库并拿到本地 id。
     *
     * <p>复用 {@link PaperService#resolve}:它已经是并发安全的幂等 upsert ——
     * 多个用户同时搜到同一篇论文、或同一用户重复搜同一个词,都只会有一行。
     */
    private PaperResponse persist(AiSearchResponse.Paper paper) {
        PaperInput input = new PaperInput(
                paper.source(),
                paper.externalId(),
                paper.doi(),
                paper.arxivId(),
                paper.title(),
                paper.authors(),
                paper.abstractText(),
                paper.publicationYear(),
                paper.venue(),
                paper.url());

        return PaperResponse.of(paperService.resolve(input));
    }
}
