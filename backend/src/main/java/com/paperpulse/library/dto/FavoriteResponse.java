package com.paperpulse.library.dto;

import com.paperpulse.library.PaperFavorite;
import com.paperpulse.paper.Paper;
import com.paperpulse.paper.dto.PaperResponse;

import java.time.Instant;

/**
 * 一条收藏记录。
 *
 * <p>论文元数据**内嵌**而不是只给一个 paperId:否则前端拿到收藏列表后,
 * 还得为每一条再请求一次论文详情 —— 一屏二十条就是二十次往返。
 *
 * @param paper       被收藏的论文
 * @param favoritedAt 收藏时间
 */
public record FavoriteResponse(PaperResponse paper, Instant favoritedAt) {

    public static FavoriteResponse of(Paper paper, PaperFavorite favorite) {
        return new FavoriteResponse(PaperResponse.of(paper), favorite.getFavoritedAt());
    }
}
