package com.paperpulse.reading;

import com.paperpulse.common.ApiException;
import com.paperpulse.reading.dto.AiArxivLookupResponse;
import com.paperpulse.reading.dto.QaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/** 调用 ai-service 的精读问答。 */
@Component
public class AiReadingClient {

    private static final Logger log = LoggerFactory.getLogger(AiReadingClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiReadingClient(RestClient aiServiceRestClient, ObjectMapper objectMapper) {
        this.restClient = aiServiceRestClient;
        this.objectMapper = objectMapper;
    }

    public QaResponse ask(String arxivId, String question, List<Map<String, String>> history) {
        try {
            QaResponse response = restClient.post()
                    .uri("/qa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("arxivId", arxivId, "question", question, "history", history))
                    .retrieve()
                    .body(QaResponse.class);

            if (response == null) {
                throw ApiException.badGateway("精读服务返回了空响应");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw mapUpstreamError(ex);
        } catch (RestClientException ex) {
            log.warn("连接 ai-service 失败", ex);
            throw ApiException.serviceUnavailable("精读服务不可用,请确认 ai-service 已启动");
        }
    }

    /** 按编号取一篇 arXiv 论文的元数据。 */
    public AiArxivLookupResponse lookupArxiv(String arxivId) {
        try {
            AiArxivLookupResponse response = restClient.post()
                    .uri("/lookup/arxiv")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("arxivId", arxivId))
                    .retrieve()
                    .body(AiArxivLookupResponse.class);

            if (response == null) {
                throw ApiException.badGateway("精读服务返回了空响应");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw mapUpstreamError(ex);
        } catch (RestClientException ex) {
            log.warn("连接 ai-service 失败", ex);
            throw ApiException.serviceUnavailable("精读服务不可用,请确认 ai-service 已启动");
        }
    }

    /**
     * 把上游的状态码翻译成本服务对外的语义。
     *
     * <p>**404 要单独处理**:它在这里的意思是"这篇论文没有可精读的全文",
     * 是**资源的性质**,不是"服务器找不到东西"。原样透传会让调用方以为是自己传错了 id。
     */
    private ApiException mapUpstreamError(RestClientResponseException ex) {
        String detail = upstreamDetail(ex);
        int status = ex.getStatusCode().value();
        log.warn("ai-service 返回 {}:{}", status, ex.getResponseBodyAsString());

        return switch (status) {
            // 这篇没有全文 —— 告诉用户"这篇读不了",而不是"服务出错"
            case 404 -> ApiException.badRequest(detail);
            // 论文本身超出了能处理的长度
            case 422 -> ApiException.badRequest(detail);
            default -> ApiException.badGateway("精读服务出错(HTTP " + status + ")");
        };
    }

    /** FastAPI 的错误体是 {@code {"detail": "..."}}。取不到就回落到一句兜底。 */
    private String upstreamDetail(RestClientResponseException ex) {
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            JsonNode detail = body.get("detail");
            if (detail != null && !detail.isNull()) {
                return detail.asString();
            }
        } catch (RuntimeException ignored) {
            // 解析不出来不是错误路径上的重点,回落到兜底消息即可
        }
        return "这篇论文暂时无法精读";
    }
}
