package com.paperpulse.library.dto;

import com.paperpulse.library.PaperRating;
import com.paperpulse.paper.Paper;
import com.paperpulse.paper.dto.PaperResponse;

import java.time.Instant;

/**
 * 一条评分记录。
 *
 * @param paper   被打分的论文
 * @param score   1~5
 * @param ratedAt 最近一次打分的时间
 */
public record RatingResponse(PaperResponse paper, int score, Instant ratedAt) {

    public static RatingResponse of(Paper paper, PaperRating rating) {
        return new RatingResponse(PaperResponse.of(paper), rating.getScore(), rating.getRatedAt());
    }
}
