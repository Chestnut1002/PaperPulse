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

    public PaperService(PaperRepository paperRepository, PlatformTransactionManager transactionManager) {
        this.paperRepository = paperRepository;
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

        try {
            return Objects.requireNonNull(
                    writeTransaction.execute(status -> createOrUpdate(source, input)),
                    "写事务没有返回结果");
        } catch (DataIntegrityViolationException ex) {
            // 并发首次提交同一篇论文:另一个请求刚刚把它插进去了,我们撞在唯一约束上。
            // 对方的事务已经提交,所以这里重查必定查得到。
            // 查不到就说明冲突另有原因(比如别的约束),把原异常抛回去,不要吞掉。
            return paperRepository.findBySourceAndExternalId(source, input.externalId())
                    .orElseThrow(() -> ex);
        }
    }

    /** 查到了就更新元数据,没查到就新建。**必须在 {@link #writeTransaction} 里调用。** */
    private Paper createOrUpdate(PaperSource source, PaperInput input) {
        Instant now = Instant.now();

        return paperRepository.findBySourceAndExternalId(source, input.externalId())
                .map(existing -> {
                    existing.applyMetadata(input, now);
                    return paperRepository.save(existing);
                })
                .orElseGet(() ->
                        // saveAndFlush 而不是 save:要在**这个方法内**就把 INSERT 发出去,
                        // 唯一约束冲突才会在这里浮出来,而不是拖到事务提交时 —— 那时已经没有重查的机会了。
                        paperRepository.saveAndFlush(new Paper(source, input, now)));
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
