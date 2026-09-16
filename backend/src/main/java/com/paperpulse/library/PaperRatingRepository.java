package com.paperpulse.library;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 评分仓储(F6)。 */
public interface PaperRatingRepository extends JpaRepository<PaperRating, Long> {

    /** 某用户的评分,最近打的排在前面。 */
    List<PaperRating> findByUserIdOrderByRatedAtDesc(Long userId);

    Optional<PaperRating> findByUserIdAndPaperId(Long userId, Long paperId);

    /** 取消评分,返回真正删掉的行数 —— 0 表示本来就没评过,要回 404。 */
    @Modifying
    @Query("delete from PaperRating r where r.userId = :userId and r.paperId = :paperId")
    int deleteOwned(@Param("userId") Long userId, @Param("paperId") Long paperId);
}
