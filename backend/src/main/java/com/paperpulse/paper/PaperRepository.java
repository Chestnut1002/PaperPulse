package com.paperpulse.paper;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
