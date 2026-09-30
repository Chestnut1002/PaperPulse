package com.paperpulse.paper;

import com.paperpulse.paper.dto.PaperInput;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.List;

/**
 * 一篇论文的元数据(F6)。
 *
 * <p><b>为什么元数据要落本地库,而不是只存一个外部 ID:</b>
 * 只存 ID 的话,每次打开收藏夹都要回源 Semantic Scholar。而 S2 的 429 是**实测会发生的**
 * (见 F5 期间对 {@code api.semanticscholar.org} 的观测),那样 S2 一抽风用户的收藏夹就打不开了 ——
 * 一个"我的收藏"页面依赖外部服务的可用性,是不该有的耦合。
 * 另外 REQ-006 的离线评测需要一个可以反复查、不会变的本地语料。
 *
 * <p><b>一篇论文在库里只有一份。</b>唯一约束 {@code (source, external_id)} 保证多个用户收藏
 * 同一篇论文时不会各存一份,它们指向同一行。收藏/历史/评分表引用的是本表的主键。
 *
 * <p><b>跨来源的同一篇论文也应当只有一份</b>,靠的是 DOI(见 {@link #doi})。
 * 检索会在 Semantic Scholar 与 Crossref 之间降级,同一个查询昨天走 A、今天走 B,
 * 只按来源去重就会把同一篇论文存成两行,用户的收藏与评分随之分裂。
 *
 * <p><b>元数据是可更新的,但不接受"用空值覆盖"。</b>不同来源、不同时间返回的字段完整度不一样
 * (有的没有摘要,有的没有会议名)。{@link #applyMetadata} 只覆盖非空字段,
 * 免得一次信息不全的请求把已有的摘要抹掉。
 */
@Entity
@Table(name = "paper",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_paper_source_external_id",
                        columnNames = {"source", "external_id"}),
                @UniqueConstraint(
                        name = "uk_paper_doi",
                        columnNames = {"doi"})
        },
        // 跨源合并要按"归一化标题 + 年份"找同一篇的另一个版本,没有索引就得全表扫
        indexes = @Index(name = "idx_paper_title_key", columnList = "title_key"))
public class Paper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 来源库。存枚举名而不是序号 —— 序号会随枚举顺序变动而错位。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaperSource source;

    /** 来源库里的 ID(如 S2 的 paperId、arXiv 的编号)。 */
    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    /**
     * DOI。**跨源身份**:同一篇论文在 Semantic Scholar 和 Crossref 下的来源与外部 ID 都不同,
     * 但 DOI 相同 —— 只按 {@code (source, external_id)} 去重会把同一篇论文存成两行,
     * 用户的收藏 / 评分 / 阅读历史也就跟着分裂。
     *
     * <p>可空,且唯一索引允许多行 NULL(MySQL 如此),所以没有 DOI 的论文互不冲突,
     * 它们继续走 {@code (source, external_id)} 那套。
     */
    @Column(name = "doi", length = 128)
    private String doi;

    /**
     * arXiv 编号。**它决定这篇论文能不能精读** —— 全文是从 arXiv 的 HTML 版抓的。
     *
     * <p>可空:期刊论文没有。Semantic Scholar 索引进 arXiv 的记录会带这个编号,
     * Crossref 那边则要看 DOI 是不是 arXiv 的({@code 10.48550/arxiv.*})。
     */
    @Column(name = "arxiv_id", length = 64)
    private String arxivId;

    @Column(nullable = false, length = 512)
    private String title;

    /**
     * 归一化后的标题,供跨源合并按标题查询({@link PaperMatcher#titleKey})。
     *
     * <p>之所以单独存一列而不是查的时候现算:现算就得对全表做函数运算,索引用不上。
     * 它必须与 {@link #title} **同时更新**,否则会出现"标题改了但键没改"的静默不一致。
     */
    @Column(name = "title_key", length = 512)
    private String titleKey;

    /** 作者名列表,以 JSON 数组存进一个文本列。见 {@link StringListConverter}。 */
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> authors;

    /**
     * 摘要。
     *
     * <p>列名是 {@code abstract_text} 而不是 {@code abstract}:后者虽然不是 MySQL 保留字,
     * 但 Java 字段名不能叫 {@code abstract},两边不一致反而更容易读错。
     */
    @Column(name = "abstract_text", columnDefinition = "TEXT")
    private String abstractText;

    /** 发表年份。用包装类型:年份未知就是 null,不该被当成 0 年。 */
    @Column(name = "publication_year")
    private Integer publicationYear;

    /** 会议或期刊名。 */
    @Column(length = 256)
    private String venue;

    /** 原文链接。 */
    @Column(length = 512)
    private String url;

    /** 元数据最后一次被更新的时间。用来判断手里的信息有多旧。 */
    @Column(name = "metadata_updated_at", nullable = false)
    private Instant metadataUpdatedAt;

    /** JPA 要求实体必须有公开或受保护的无参构造函数。 */
    protected Paper() {
    }

    public Paper(PaperSource source, PaperInput input, Instant now) {
        this.source = source;
        this.externalId = input.externalId();
        this.doi = Doi.normalize(input.doi());
        this.arxivId = normalizeArxivId(input.arxivId());
        this.metadataUpdatedAt = now;
        // 构造函数里直接赋值,不走 applyMetadata —— 新建的行没有"已有数据"需要保护。
        this.title = input.title();
        this.titleKey = PaperMatcher.titleKey(input.title());
        this.authors = List.copyOf(input.authors());
        this.abstractText = input.abstractText();
        this.publicationYear = input.publicationYear();
        this.venue = input.venue();
        this.url = input.url();
    }

    /**
     * 用新拿到的元数据更新本行。
     *
     * <p><b>只覆盖非空字段。</b>同一个来源在不同时刻返回的字段完整度不一样;
     * 如果无条件覆盖,一次信息不全的响应(比如搜索接口默认不返回摘要)就会把已经存好的摘要清空。
     * 元数据只会变全,不会因为一次请求而变少。
     *
     * @return 是否有字段真的发生了变化
     */
    public boolean applyMetadata(PaperInput input, Instant now) {
        boolean changed = false;

        // DOI 也在这里补齐:先入库的那次可能没带 DOI(比如降级到某个字段更少的源),
        // 后来拿到了就补上。**这不会覆盖已有的 DOI** —— 同一篇论文的 DOI 只会有一个。
        changed |= replaceIfPresent(doi, Doi.normalize(input.doi()), value -> doi = value);
        changed |= replaceIfPresent(arxivId, normalizeArxivId(input.arxivId()), value -> arxivId = value);

        // 标题与 title_key 必须一起改
        changed |= replaceIfPresent(title, input.title(), value -> {
            title = value;
            titleKey = PaperMatcher.titleKey(value);
        });
        changed |= replaceIfPresent(abstractText, input.abstractText(), value -> abstractText = value);
        changed |= replaceIfPresent(venue, input.venue(), value -> venue = value);
        changed |= replaceIfPresent(url, input.url(), value -> url = value);
        changed |= replaceIfPresent(publicationYear, input.publicationYear(), value -> publicationYear = value);

        // 作者列表:传了非空列表才覆盖。空列表视为"这次没带作者",不是"这篇论文没有作者"。
        if (!input.authors().isEmpty() && !input.authors().equals(authors)) {
            this.authors = List.copyOf(input.authors());
            changed = true;
        }

        if (changed) {
            this.metadataUpdatedAt = now;
        }
        return changed;
    }

    /**
     * 只补空缺、不覆盖已有值。
     *
     * <p><b>用在跨源合并上。</b>{@link #applyMetadata} 是"不同就覆盖",那套语义在这里会让标题来回翻:
     * arXiv 版的标题带"(Extended Version)",覆盖一次;下次正式版来了又覆盖回去。
     * 而两个版本的差异是**变体**,不是**更正**。
     *
     * <p>效果正好是互补的:正式版补上会议名,预印本补上摘要。
     *
     * @return 是否有字段真的被补上
     */
    public boolean fillMissing(PaperInput input, Instant now) {
        boolean changed = false;

        changed |= fillIfAbsent(doi, Doi.normalize(input.doi()), value -> doi = value);
        changed |= fillIfAbsent(arxivId, normalizeArxivId(input.arxivId()), value -> arxivId = value);
        changed |= fillIfAbsent(title, input.title(), value -> {
            title = value;
            titleKey = PaperMatcher.titleKey(value);
        });
        changed |= fillIfAbsent(abstractText, input.abstractText(), value -> abstractText = value);
        changed |= fillIfAbsent(venue, input.venue(), value -> venue = value);
        changed |= fillIfAbsent(url, input.url(), value -> url = value);
        changed |= fillIfAbsent(publicationYear, input.publicationYear(), value -> publicationYear = value);

        if (getAuthors().isEmpty() && !input.authors().isEmpty()) {
            this.authors = List.copyOf(input.authors());
            changed = true;
        }

        if (changed) {
            this.metadataUpdatedAt = now;
        }
        return changed;
    }

    /**
     * 补上 arXiv 编号(**只填空缺**)。用在 REQ-003 P3 的标题反查上:
     * 查到预印本后把编号写进这一行,这篇从此能精读,而且**所有用户**都不必再查一次。
     *
     * <p>用 {@link #fillIfAbsent} 而不是覆盖:同一篇论文的编号只会有一个,
     * 已有值时再写一次没有意义,还会把 {@code metadataUpdatedAt} 无谓地推新。
     *
     * @return 是否真的写入了
     */
    public boolean attachArxivId(String raw, Instant now) {
        boolean changed = fillIfAbsent(arxivId, normalizeArxivId(raw), value -> arxivId = value);
        if (changed) {
            this.metadataUpdatedAt = now;
        }
        return changed;
    }

    /**
     * arXiv 编号的规范化:去掉版本后缀。
     *
     * <p>{@code 2502.19271v2} 与 {@code 2502.19271} 是**同一篇的不同修订**,
     * 带着版本号存会让同一个编号认不出来 —— 而它是"这篇能不能精读"的判据。
     */
    private static String normalizeArxivId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().replaceFirst("v\\d+$", "");
    }

    /** 当前值为空、且新值非空时才写入。返回是否写入。 */
    private static <T> boolean fillIfAbsent(T current, T incoming,
                                            java.util.function.Consumer<T> setter) {
        boolean hasCurrent = current != null && !(current instanceof String text && text.isBlank());
        if (hasCurrent) {
            return false;
        }
        boolean hasIncoming = incoming != null && !(incoming instanceof String text && text.isBlank());
        if (!hasIncoming) {
            return false;
        }
        setter.accept(incoming);
        return true;
    }

    /** 新值非空(字符串还要非空白)时才写入。返回是否写入。 */
    private static <T> boolean replaceIfPresent(T current, T incoming, java.util.function.Consumer<T> setter) {
        if (incoming == null || (incoming instanceof String text && text.isBlank())) {
            return false;
        }
        if (incoming.equals(current)) {
            return false;
        }
        setter.accept(incoming);
        return true;
    }

    // ── 只读访问 ────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public PaperSource getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getDoi() {
        return doi;
    }

    public String getArxivId() {
        return arxivId;
    }

    public String getTitle() {
        return title;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public List<String> getAuthors() {
        return authors == null ? List.of() : authors;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }

    public String getVenue() {
        return venue;
    }

    public String getUrl() {
        return url;
    }

    public Instant getMetadataUpdatedAt() {
        return metadataUpdatedAt;
    }
}
