package com.paperpulse.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * 假的 ai-service:用 JDK 自带的 {@link HttpServer} 起一个**真的** HTTP 服务。
 *
 * <p><b>为什么不把 {@code AiSearchClient} mock 掉:</b>那样会绕过 JSON 反序列化、状态码映射、
 * 请求体形状、超时配置 —— 而这几处恰恰是最容易出错的地方。用一个真的 HTTP 服务,
 * 被测代码走的是完整的真实路径,只有"对面是谁"被换掉了。
 *
 * <p>不引 WireMock 之类的库:JDK 自带就够,少一个依赖。
 */
public class StubAiService {

    private final HttpServer server;

    /** 收到的请求体,按到达顺序。用来断言"我们确实把查询和条数发出去了"。 */
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();

    private volatile int responseStatus = 200;
    private volatile String responseBody = "{}";

    public StubAiService() {
        try {
            // 端口传 0 让系统分配一个空闲端口,避免与其它测试或本机服务撞车
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException("无法启动桩服务", ex);
        }

        // 用守护线程:非守护线程会让 JVM 在测试跑完后不退出,表现为构建挂住
        server.setExecutor(Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "stub-ai-service");
            thread.setDaemon(true);
            return thread;
        }));

        server.createContext("/search", exchange -> {
            receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));

            byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(responseStatus, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });

        server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + getPort();
    }

    /** 下一次请求返回什么。用例通过它来模拟正常响应与各种错误。 */
    public void respondWith(int status, String body) {
        this.responseStatus = status;
        this.responseBody = body;
    }

    public List<String> receivedBodies() {
        return List.copyOf(receivedBodies);
    }

    public void reset() {
        receivedBodies.clear();
        respondWith(200, "{}");
    }
}
