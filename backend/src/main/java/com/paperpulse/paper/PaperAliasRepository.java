package com.paperpulse.paper;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 论文别名的仓储。 */
public interface PaperAliasRepository extends JpaRepository<PaperAlias, Long> {

    /**
     * 按"来源 + 外部 ID"查别名。
     *
     * <p>唯一约束保证至多一条 —— 一个身份不会同时指向两行。
     */
    Optional<PaperAlias> findBySourceAndExternalId(PaperSource source, String externalId);
}
