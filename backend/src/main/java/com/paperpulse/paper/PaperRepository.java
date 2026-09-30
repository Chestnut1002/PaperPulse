package com.paperpulse.paper;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 论文仓储(F6)。 */
public interface PaperRepository extends JpaRepository<Paper, Long> {

    /**
     * 按"来源 + 外部 ID"查,这是论文的天然主键。
     *
     * <p>两个条件缺一不可:只按 externalId 查会把不同来源的撞号记录错认成同一篇。
     */
    Optional<Paper> findBySourceAndExternalId(PaperSource source, String externalId);

    /**
     * 按 DOI 查 —— **跨源认人的那条线**。
     *
     * <p>检索会在 Semantic Scholar 与 Crossref 之间降级,同一个查询昨天走 A、今天走 B。
     * 只按 {@code (source, externalId)} 查会把同一篇论文当成两篇,所以有 DOI 时优先用这条。
     *
     * <p>调用方必须传规范化之后的值(见 {@link Doi#normalize}),否则大小写或前缀的差异会查不到。
     */
    Optional<Paper> findByDoi(String doi);

    /**
     * 按"归一化标题 + 年份区间"找**可能是同一篇的另一个版本**的那些行。
     *
     * <p>这是跨源合并的倒数第二步:到这一步说明身份是新的,但内容可能已经以别的身份在库里了。
     *
     * <p><b>只按标题和年份筛,作者留给调用方在内存里比</b> —— 作者是以 JSON 存在一列里的,
     * SQL 比不了。年份区间由 {@link PaperMatcher#YEAR_TOLERANCE} 决定;
     * 标题相同的候选通常只有个位数,内存里比作者完全够用。
     */
    List<Paper> findByTitleKeyAndPublicationYearBetween(String titleKey, int fromYear, int toYear);
}
