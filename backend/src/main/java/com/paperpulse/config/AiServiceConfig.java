package com.paperpulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * ai-service 的 HTTP 客户端配置。
 *
 * <p>检索与推荐都要调它,把 {@link RestClient} 收到一处 —— 免得两边各配一遍超时,
 * 改了一处漏一处,而超时配错的表现是"偶尔卡住",最难查。
 */
@Configuration
public class AiServiceConfig {

    @Bean
    public RestClient aiServiceRestClient(@Value("${app.ai-service.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // 连接超时短:连不上就要立刻说"服务没起",而不是让用户对着转圈等半分钟
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        // 读超时长:一次调用里可能有大模型拆解与多次外部检索,慢起来能到几十秒
        requestFactory.setReadTimeout(Duration.ofSeconds(120));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
