package com.paperpulse.library;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 阅读历史仓储(F6)。 */
public interface PaperReadHistoryRepository extends JpaRepository<PaperReadHistory, Long> {

    /**
     * 某用户的阅读历史,最近读的排在前面。
     *
     * <p>接 {@link Pageable} 是为了能只取前 N 条。历史会随时间无限增长,
     * 而"最近读了什么"只需要开头这一段 —— 不带限制地全查出来,几年后就是一次全表扫描。
     */
    List<PaperReadHistory> findByUserIdOrderByLastReadAtDesc(Long userId, Pageable pageable);

    Optional<PaperReadHistory> findByUserIdAndPaperId(Long userId, Long paperId);

    /** 清空某用户的全部阅读历史,返回删掉的行数。 */
    @Modifying
    @Query("delete from PaperReadHistory h where h.userId = :userId")
    int deleteAllOwned(@Param("userId") Long userId);
}
