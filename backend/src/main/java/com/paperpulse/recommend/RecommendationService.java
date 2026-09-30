package com.paperpulse.recommend;

import com.paperpulse.interest.InterestService;
import com.paperpulse.interest.InterestTag;
import com.paperpulse.interest.dto.UserInterestResponse;
import com.paperpulse.library.LibraryService;
import com.paperpulse.paper.PaperService;
import com.paperpulse.paper.dto.PaperInput;
import com.paperpulse.paper.dto.PaperResponse;
import com.paperpulse.recommend.dto.AiCandidateResponse;
import com.paperpulse.recommend.dto.RecommendationResponse;
import com.paperpulse.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 个性化推荐(REQ-004)。
 *
 * <p><b>这一版是确定性打分,不用大模型。</b>三个理由:兴趣标签本身就带着检索词,没有需要拆解的自然语言;
 * 打分要可解释、可复现、可测试;而最重要的是 —— **它是 REQ-006 离线评测的基线**。
 * 没有基线,以后那个"LLM 生成式推荐"到底有没有变好,根本说不清。
 *
 * <p>链路:兴趣标签 → 取候选(外部检索)→ 排除已知 → 打分 → 排探索位 → 返回带理由的列表。
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    /**
     * 用权重最高的几个兴趣去取候选。
     *
     * <p>不设上限的话,十个标签就是十组检索(每组三个源)—— 又慢又招外部接口烦。
     * 四个够覆盖"主要方向 + 一点探索"了。
     */
    private static final int MAX_QUERY_TAGS = 4;

    /** 每路取多少条候选。要比最终条数多,才有得挑。 */
    private static final int PER_QUERY = 8;

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;

    /** 探索位默认比例。沿用 PaperAgent 的取值。 */
    private static final double DEFAULT_DIVERSITY_RATIO = 0.3;

    /** 兴趣匹配:标签权重(1~5)乘以它。**这是主导项** —— "对不对口"比"新不新"更重要。 */
    private static final int INTEREST_WEIGHT = 2;

    /** 新近度的最大贡献。刻意比兴趣低一档。 */
    private static final int RECENCY_MAX = 4;

    /** 超过这个年头就不再给新近度加分。 */
    private static final int RECENCY_WINDOW = 4;

    private static final String NO_INTERESTS_HINT =
            "还没有选兴趣标签 —— 先去「兴趣标签」里选几个,推荐才有依据。";

    private final AiRecommendClient aiRecommendClient;
    private final InterestService interestService;
    private final LibraryService libraryService;
    private final PaperService paperService;
    private final UserService userService;

    public RecommendationService(AiRecommendClient aiRecommendClient,
                                 InterestService interestService,
                                 LibraryService libraryService,
                                 PaperService paperService,
                                 UserService userService) {
        this.aiRecommendClient = aiRecommendClient;
        this.interestService = interestService;
        this.libraryService = libraryService;
        this.paperService = paperService;
        this.userService = userService;
    }

    public RecommendationResponse recommend(Long userId, Integer limit, Double diversityRatio) {
        // 回查用户:token 签名有效不等于用户仍然存在(与其余接口同一个落点)
        userService.getById(userId);

        int size = clampLimit(limit);
        double ratio = clampRatio(diversityRatio);

        List<UserInterestResponse> interests = interestService.listFor(userId);
        if (interests.isEmpty()) {
            return new RecommendationResponse(null, ratio, NO_INTERESTS_HINT, List.of());
        }

        Map<String, Integer> weightByTag = interests.stream()
                .collect(Collectors.toMap(UserInterestResponse::tag, UserInterestResponse::weight,
                        (first, second) -> first, LinkedHashMap::new));
        Map<String, String> displayByTag = interests.stream()
                .collect(Collectors.toMap(UserInterestResponse::tag, UserInterestResponse::displayName,
                        (first, second) -> first, LinkedHashMap::new));

        AiCandidateResponse found = aiRecommendClient.findCandidates(
                toQueries(topTags(interests)), PER_QUERY);

        Set<Long> known = libraryService.knownPaperIds(userId);
        int currentYear = Year.now().getValue();

        List<Scored> candidates = candidatesOf(found).stream()
                .filter(RecommendationService::isComplete)
                // 落库、并把"命中了哪些兴趣"一起带上 —— 那个信息只在候选那边有,
                // 落库之后 {@link PaperResponse} 里没有
                .map(candidate -> new Resolved(persist(candidate), matchedTagsOf(candidate)))
                .filter(resolved -> !known.contains(resolved.paper().id()))
                .map(resolved -> score(resolved, weightByTag, currentYear))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .toList();

        List<RecommendationResponse.Item> items = pick(candidates, size, ratio).stream()
                .map(scored -> toItem(scored, weightByTag, displayByTag))
                .toList();

        return new RecommendationResponse(found.sourceLabel(), ratio, null, items);
    }

    // ── 候选 ────────────────────────────────────────────────

    private static List<AiCandidateResponse.Candidate> candidatesOf(AiCandidateResponse found) {
        return found.papers() == null ? List.of() : found.papers();
    }

    private static List<String> matchedTagsOf(AiCandidateResponse.Candidate candidate) {
        return candidate.matchedTags() == null ? List.of() : List.copyOf(candidate.matchedTags());
    }

    /** 权重高的排前面;权重相同时按原有顺序(词表顺序),保证结果稳定。 */
    private static List<InterestTag> topTags(List<UserInterestResponse> interests) {
        return interests.stream()
                .sorted(Comparator.comparingInt(UserInterestResponse::weight).reversed())
                .limit(MAX_QUERY_TAGS)
                .map(interest -> InterestTag.findByKey(interest.tag()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static List<AiRecommendClient.Query> toQueries(List<InterestTag> tags) {
        return tags.stream()
                .map(tag -> new AiRecommendClient.Query(tag.key(), tag.s2Query()))
                .toList();
    }

    private static boolean isComplete(AiCandidateResponse.Candidate candidate) {
        boolean complete = isPresent(candidate.source())
                && isPresent(candidate.externalId())
                && isPresent(candidate.title());
        if (!complete) {
            log.warn("候选元数据不全,已跳过:source={} externalId={}",
                    candidate.source(), candidate.externalId());
        }
        return complete;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    /** 落库拿本地 id —— 复用与检索同一条路径,跨源合并会自动生效。 */
    private PaperResponse persist(AiCandidateResponse.Candidate candidate) {
        return PaperResponse.of(paperService.resolve(new PaperInput(
                candidate.source(),
                candidate.externalId(),
                candidate.doi(),
                candidate.arxivId(),
                candidate.title(),
                candidate.authors(),
                candidate.abstractText(),
                candidate.publicationYear(),
                candidate.venue(),
                candidate.url())));
    }

    // ── 打分 ────────────────────────────────────────────────

    /** 落库后的候选:论文本体 + 它命中了哪几个兴趣。 */
    private record Resolved(PaperResponse paper, List<String> matchedTags) {
    }

    private record Scored(PaperResponse paper, List<String> matchedTags, int interest, int recency) {
        double score() {
            return (double) interest * INTEREST_WEIGHT + recency;
        }
    }

    private static Scored score(Resolved resolved, Map<String, Integer> weightByTag, int currentYear) {
        int interest = resolved.matchedTags().stream()
                .mapToInt(tag -> weightByTag.getOrDefault(tag, 0))
                .max()
                .orElse(0);
        return new Scored(resolved.paper(), resolved.matchedTags(), interest,
                recencyScore(resolved.paper().publicationYear(), currentYear));
    }

    /**
     * 新近度:越新越高,超过窗口就不再给分。
     *
     * <p>**年份未知时给 0 而不是倒扣** —— 缺年份是"记录没写",不是"这篇很旧"。
     * 有些来源(尤其 Crossref)确实不总给年份,倒扣会系统性压低它们的候选。
     */
    private static int recencyScore(Integer year, int currentYear) {
        if (year == null) {
            return 0;
        }
        int age = Math.max(0, currentYear - year);
        return Math.max(0, RECENCY_MAX - Math.min(age, RECENCY_WINDOW));
    }

    // ── 探索位 ──────────────────────────────────────────────

    /**
     * 选出最终要展示的那几篇:**大部分按分数取,预留一部分名额给"没命中主要兴趣"的候选**。
     *
     * <p>不预留的话,用户只看得到自己最强的那一个方向,越推越窄 —— 这就是过滤气泡。
     *
     * <p><b>探索位决定"谁能进榜",不决定顺序。</b>选完之后仍按分数排 ——
     * 否则会出现"2013 年的论文排在第 3、2026 年的排在它后面",列表看起来根本没排序。
     * 而探索位的候选本来就因为兴趣权重低而分数偏低,排序后自然会落到后半段,该被看到的仍然看得到。
     */
    private static List<Scored> pick(List<Scored> sorted, int limit, double ratio) {
        if (sorted.isEmpty()) {
            return List.of();
        }

        // 基准取"命中次数最多的兴趣",而不是"分数最高那篇命中的标签" —— 后者只要一篇的顺序变了就跟着变
        String topTag = mostFrequentTag(sorted);

        List<Scored> exploitation = new ArrayList<>();
        List<Scored> exploration = new ArrayList<>();
        for (Scored scored : sorted) {
            if (topTag != null && scored.matchedTags().contains(topTag)) {
                exploitation.add(scored);
            } else {
                exploration.add(scored);
            }
        }

        int exploreSlots = (int) Math.round(limit * ratio);
        // 两边各留至少一个位置:否则参数极端时会退化成"全是探索"或"探索位形同虚设"
        exploreSlots = Math.max(0, Math.min(exploreSlots, limit - 1));

        List<Scored> picked = new ArrayList<>();
        exploitation.stream().limit(limit - exploreSlots).forEach(picked::add);
        exploration.stream().limit(exploreSlots).forEach(picked::add);

        // 任意一边不够,就用另一边补满 —— 宁可少一点探索,也不要返回不满一页
        if (picked.size() < limit) {
            Stream.concat(exploitation.stream(), exploration.stream())
                    .filter(candidate -> !picked.contains(candidate))
                    .limit(limit - picked.size())
                    .forEach(picked::add);
        }

        picked.sort(Comparator.comparingDouble(Scored::score).reversed());
        return picked;
    }

    private static String mostFrequentTag(List<Scored> scored) {
        return scored.stream()
                .flatMap(item -> item.matchedTags().stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()))
                .entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    // ── 理由 ────────────────────────────────────────────────

    private static RecommendationResponse.Item toItem(Scored scored,
                                                      Map<String, Integer> weightByTag,
                                                      Map<String, String> displayByTag) {
        return new RecommendationResponse.Item(
                scored.paper(), scored.score(),
                reason(scored, weightByTag, displayByTag),
                scored.matchedTags());
    }

    /** REQ-005 的最简版本:说清命中了哪个兴趣、权重多少,顺带带上年份。 */
    private static String reason(Scored scored, Map<String, Integer> weightByTag,
                                 Map<String, String> displayByTag) {
        Optional<String> best = scored.matchedTags().stream()
                .max(Comparator.comparingInt(tag -> weightByTag.getOrDefault(tag, 0)));

        String base = best
                .map(tag -> "命中你的兴趣「" + displayByTag.getOrDefault(tag, tag)
                        + "」(权重 " + weightByTag.getOrDefault(tag, 0) + ")")
                .orElse("与你的研究方向相关");

        Integer year = scored.paper().publicationYear();
        return year == null ? base : base + " · " + year + " 年";
    }

    // ── 参数 ────────────────────────────────────────────────

    private static int clampLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static double clampRatio(Double ratio) {
        if (ratio == null || ratio < 0 || ratio > 1) {
            return DEFAULT_DIVERSITY_RATIO;
        }
        return ratio;
    }
}
