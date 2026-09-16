package com.paperpulse.library;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 收藏仓储(F6)。 */
public interface PaperFavoriteRepository extends JpaRepository<PaperFavorite, Long> {

    /** 某用户的收藏,最近收藏的排在前面。 */
    List<PaperFavorite> findByUserIdOrderByFavoritedAtDesc(Long userId);

    Optional<PaperFavorite> findByUserIdAndPaperId(Long userId, Long paperId);

    /**
     * 取消收藏,返回真正删掉的行数。
     *
     * <p>返回行数是为了区分"删掉了"和"本来就没收藏" —— 后者要回 404,
     * 不能假装成功,否则前端拿着一个过期的界面点删除,会以为删掉了。
     *
     * <p>写成 JPQL 而不是派生的 delete:派生的做法会先查出实体再逐个标记删除,
     * 白白多一轮查询,而且拿不到确切的影响行数。这里的 JPQL 是当场执行的。
     */
    @Modifying
    @Query("delete from PaperFavorite f where f.userId = :userId and f.paperId = :paperId")
    int deleteOwned(@Param("userId") Long userId, @Param("paperId") Long paperId);
}
