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
}
