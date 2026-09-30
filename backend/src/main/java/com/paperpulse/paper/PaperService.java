package com.paperpulse.paper;

import com.paperpulse.common.ApiException;
import com.paperpulse.paper.dto.PaperInput;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 论文元数据的读写(F6)。
 *
 * <p>核心是 {@link #resolve}:把前端提交的元数据变成库里的一行,**已存在就更新,不存在就新建**。
 */
@Service
public class PaperService {

    private final PaperRepository paperRepository;
    private final PaperAliasRepository paperAliasRepository;

    /**
     * 用于 {@link #resolve} 的写事务,**刻意用编程式事务而不是 {@code @Transactional}`**。
     *
     * <p>原因有两个,少一个都会出错:
     * <ol>
     *   <li><b>需要单独的事务边界。</b>{@code resolve} 要捕获唯一约束冲突然后重查。
     *       如果建行和重查在同一个事务里,Hibernate 的会话在刷盘失败后已经处于不可用状态,
     *       紧接着的查询会跟着失败 —— 想恢复也恢复不了。</li>
     *   <li><b>不能被自调用绕过。</b>把 {@code @Transactional} 标在私有方法上,
     *       或从同类里直接调自己的 {@code @Transactional} 方法,代理都不会生效,
     *       注解形同虚设且**不会有任何报错**。{@link TransactionTemplate} 没有这个陷阱。</li>
     * </ol>
     *
     * <p>用 {@code REQUIRES_NEW} 而不是默认的 {@code REQUIRED}:即便将来有人从另一个事务里
     * 调用本方法,上面第 1 条也必须成立。
     */
    private final TransactionTemplate writeTransaction;

    public PaperService(PaperRepository paperRepository,
                        PaperAliasRepository paperAliasRepository,
                        PlatformTransactionManager transactionManager) {
        this.paperRepository = paperRepository;
        this.paperAliasRepository = paperAliasRepository;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 把提交的元数据落到库里,返回库里的那一行。
     *
     * <p><b>幂等</b>:同一个 {@code (source, externalId)} 提交多少次都只有一行,
     * 所以接口层统一返回 200 —— 不区分"新建"和"已存在"。
     * 客户端真正需要的是那个本地 id,两次提交拿到同一个 id 本身就是它要的保证。
     *
     * <p><b>这里的冲突恢复是必须的,不是防御性代码。</b>实测:八个线程同时提交同一篇新论文,
     * 其中**七个**都撞上了唯一约束。数据源不在事务里 —— 论文本来就来自外部检索,
     * 多个用户同时收藏同一篇热门论文是完全正常的使用方式,不是异常场景。
     */
    public Paper resolve(PaperInput input) {
        // 来源校验放在最前面:非法来源连事务都不必开
        PaperSource source = PaperSource.fromKey(input.source());
        String doi = Doi.normalize(input.doi());

        try {
            return Objects.requireNonNull(
                    writeTransaction.execute(status -> createOrUpdate(source, input, doi)),
                    "写事务没有返回结果");
        } catch (DataIntegrityViolationException ex) {
            // 并发首次提交同一篇论文:另一个请求刚刚把它插进去了,我们撞在唯一约束上。
            // 对方的事务已经提交,所以这里重查必定查得到。
            // 查不到就说明冲突另有原因(比如别的约束),把原异常抛回去,不要吞掉。
            return findByIdentity(source, input, doi).orElseThrow(() -> ex);
        }
    }

    /**
     * 查到了就更新元数据,没查到就新建。**必须在 {@link #writeTransaction} 里调用。**
     *
     * <p>顺序是"先按身份查,查不到再看是不是某篇已有论文的另一个版本":
     * 前者是同一个身份重复提交(原有行为),后者才是跨源合并。
     */
    private Paper createOrUpdate(PaperSource source, PaperInput input, String doi) {
        Instant now = Instant.now();

        Optional<Paper> byIdentity = findByIdentity(source, input, doi);
        if (byIdentity.isPresent()) {
            Paper existing = byIdentity.get();
            existing.applyMetadata(input, now);
            return paperRepository.save(existing);
        }

        Optional<Paper> otherVersion = findOtherVersion(input);
        if (otherVersion.isPresent()) {
            return mergeInto(otherVersion.get(), source, input, now);
        }

        // saveAndFlush 而不是 save:要在**这个方法内**就把 INSERT 发出去,
        // 唯一约束冲突才会在这里浮出来,而不是拖到事务提交时 —— 那时已经没有重查的机会了。
        return paperRepository.saveAndFlush(new Paper(source, input, now));
    }

    /**
     * 把这次的记录并进已有的那一行。
     *
     * <p><b>元数据用"只补空缺"而不是"覆盖"</b>:两个版本的差异是变体不是更正
     * (arXiv 版标题带"(Extended Version)"),用覆盖语义会让标题在两次检索之间来回翻。
     *
     * <p><b>记一条别名</b> —— 少了它合并就留不住:下次再遇到这个身份,
     * 按 {@code (来源, 外部 ID)} 查不到行,又会新建一个。
     */
    private Paper mergeInto(Paper target, PaperSource source, PaperInput input, Instant now) {
        target.fillMissing(input, now);
        Paper saved = paperRepository.save(target);

        paperAliasRepository.save(
                new PaperAlias(saved.getId(), source, input.externalId(), now));
        return saved;
    }

    /**
     * 找到这篇论文已有的那一行(按身份)。
     *
     * <p>三级,由强到弱:
     * <ol>
     *   <li><b>DOI</b> —— 最强的跨源信号:两个源引用同一条已发表记录时,来源与外部 ID 都不同,但 DOI 相同</li>
     *   <li><b>(来源, 外部 ID)</b> —— 同一身份重复提交,原有行为</li>
     *   <li><b>别名</b> —— 这个身份之前被合并过,指向当时采用的那一行</li>
     * </ol>
     */
    private Optional<Paper> findByIdentity(PaperSource source, PaperInput input, String doi) {
        if (doi != null) {
            Optional<Paper> byDoi = paperRepository.findByDoi(doi);
            if (byDoi.isPresent()) {
                return byDoi;
            }
        }

        Optional<Paper> byExternalId =
                paperRepository.findBySourceAndExternalId(source, input.externalId());
        if (byExternalId.isPresent()) {
            return byExternalId;
        }

        return paperAliasRepository.findBySourceAndExternalId(source, input.externalId())
                .flatMap(alias -> paperRepository.findById(alias.getPaperId()));
    }

    /**
     * 这个身份虽然是新的,但内容可能已经以另一个身份在库里了 —— 找那一行。
     *
     * <p>只对**跨源**有意义:同一来源下的不同外部 ID 本来就是两篇(来源自己的编号体系)。
     * 不过这条不加限制也不会有害 —— 同一来源里标题、作者、年份都相同且 ID 不同的两条,
     * 本来就是那一边的数据问题。
     *
     * <p>规则见 {@link PaperMatcher}:标题 + 作者 + 年份三条件全中才算,
     * 缺任何一项都判为否 —— **宁可多一行,不可合错**。
     */
    private Optional<Paper> findOtherVersion(PaperInput input) {
        Integer year = input.publicationYear();
        String titleKey = PaperMatcher.titleKey(input.title());
        if (year == null || titleKey.isEmpty()) {
            return Optional.empty();
        }

        return paperRepository
                .findByTitleKeyAndPublicationYearBetween(
                        titleKey,
                        year - PaperMatcher.YEAR_TOLERANCE,
                        year + PaperMatcher.YEAR_TOLERANCE)
                .stream()
                .filter(candidate -> PaperMatcher.matches(
                        candidate.getTitle(), candidate.getAuthors(), candidate.getPublicationYear(),
                        input.title(), input.authors(), year))
                .findFirst();
    }

    /** 按 id 取,不存在抛 404。 */
    @Transactional(readOnly = true)
    public Paper require(Long paperId) {
        return paperRepository.findById(paperId)
                .orElseThrow(() -> ApiException.notFound("论文不存在:" + paperId));
    }

    /**
     * 批量按 id 取,返回 id 到论文的映射。
     *
     * <p>列表接口用它一次性把引用的论文全捞出来,避免每条记录再查一次库。
     * 查不到的 id 只是不出现在结果里,由调用方决定怎么处理 —— 这里不抛异常,
     * 因为"某条记录指向的论文没了"不该让整个列表打不开。
     */
    @Transactional(readOnly = true)
    public Map<Long, Paper> byIds(Collection<Long> paperIds) {
        if (paperIds.isEmpty()) {
            return Map.of();
        }
        return paperRepository.findAllById(paperIds).stream()
                .collect(Collectors.toMap(Paper::getId, Function.identity()));
    }
}
