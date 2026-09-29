package com.paperpulse.search;

import com.paperpulse.common.ApiException;
import com.paperpulse.search.dto.AiSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * 调用 ai-service 的检索接口。
 *
 * <p>只负责"把请求发出去、把响应带回来、把失败翻译成一句人话",不含任何业务判断 ——
 * 那是 {@link SearchService} 的事。
 */
@Component
public class AiSearchClient {

    private static final Logger log = LoggerFactory.getLogger(AiSearchClient.class);

    private final RestClient restClient;

    public AiSearchClient(@Value("${app.ai-service.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // 连接超时短:连不上就要立刻说"服务没起",而不是让用户对着转圈等半分钟
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        // 读超时长:一次检索包含"大模型拆解 + 外部数据库检索",慢起来能到几十秒
        requestFactory.setReadTimeout(Duration.ofSeconds(120));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** 发起一次检索。失败一律转成 {@link ApiException},不让 HTTP 细节泄漏到上层。 */
    public AiSearchResponse search(String query, int limit) {
        try {
            AiSearchResponse response = restClient.post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", query, "limit", limit))
                    .retrieve()
                    .body(AiSearchResponse.class);

            if (response == null) {
                throw ApiException.badGateway("检索服务返回了空响应");
            }
            return response;
        } catch (RestClientResponseException ex) {
            // 上游明确回了错误状态码 —— 把它的说明也记下来,排查时能少跑一趟
            log.warn("ai-service 返回 {}:{}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ApiException.badGateway("检索服务出错(HTTP " + ex.getStatusCode().value() + ")");
        } catch (RestClientException ex) {
            log.warn("连接 ai-service 失败", ex);
            throw ApiException.serviceUnavailable("检索服务不可用,请确认 ai-service 已启动");
        }
    }
}
