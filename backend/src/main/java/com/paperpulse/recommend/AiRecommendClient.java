package com.paperpulse.recommend;

import com.paperpulse.common.ApiException;
import com.paperpulse.recommend.dto.AiCandidateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * 调用 ai-service 取推荐候选。
 *
 * <p>与 {@code AiSearchClient} 分开:两者调的是不同接口、契约也不同,
 * 但共用同一个 {@link RestClient}(超时配置只有一处)。
 */
@Component
public class AiRecommendClient {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendClient.class);

    private final RestClient restClient;

    public AiRecommendClient(RestClient aiServiceRestClient) {
        this.restClient = aiServiceRestClient;
    }

    /** 一路候选检索:标签 key + 检索词。 */
    public record Query(String tag, String keywords) {
    }

    public AiCandidateResponse findCandidates(List<Query> queries, int perQuery) {
        try {
            AiCandidateResponse response = restClient.post()
                    .uri("/recommend/candidates")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("queries", queries, "perQuery", perQuery))
                    .retrieve()
                    .body(AiCandidateResponse.class);

            if (response == null) {
                throw ApiException.badGateway("推荐服务返回了空响应");
            }
            return response;
        } catch (RestClientResponseException ex) {
            log.warn("ai-service 返回 {}:{}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ApiException.badGateway("推荐服务出错(HTTP " + ex.getStatusCode().value() + ")");
        } catch (RestClientException ex) {
            log.warn("连接 ai-service 失败", ex);
            throw ApiException.serviceUnavailable("推荐服务不可用,请确认 ai-service 已启动");
        }
    }
}
