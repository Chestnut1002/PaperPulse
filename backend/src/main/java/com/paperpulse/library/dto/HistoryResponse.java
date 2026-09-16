package com.paperpulse.library.dto;

import com.paperpulse.library.PaperReadHistory;
import com.paperpulse.paper.Paper;
import com.paperpulse.paper.dto.PaperResponse;

import java.time.Instant;

/**
 * 一条阅读记录。
 *
 * <p>{@code readCount} 一并返回:前端可以直接显示"读过 3 次",
 * 而"最近阅读时间"和"次数"正是 REQ-004 需要的两个信号,这里先让它们可见。
 *
 * @param paper      读过的论文
 * @param lastReadAt 最近一次阅读的时间
 * @param readCount  累计阅读次数,从 1 开始
 */
public record HistoryResponse(PaperResponse paper, Instant lastReadAt, int readCount) {

    public static HistoryResponse of(Paper paper, PaperReadHistory history) {
        return new HistoryResponse(PaperResponse.of(paper), history.getLastReadAt(), history.getReadCount());
    }
}
