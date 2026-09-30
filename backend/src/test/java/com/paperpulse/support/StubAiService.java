package com.paperpulse.support;

import com.sun.net.httpserver.HttpExchange;
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
 * <p><b>为什么不把客户端 mock 掉:</b>那样会绕过 JSON 反序列化、状态码映射、
 * 请求体形状、超时配置 —— 而这几处恰恰是最容易出错的地方。用一个真的 HTTP 服务,
 * 被测代码走的是完整的真实路径,只有"对面是谁"被换掉了。
 *
 * <p>不引 WireMock 之类的库:JDK 自带就够,少一个依赖。
 *
 * <p>每个接口可以**分别**设定响应:检索与推荐是两条独立的链路,测试时也应当能各自出故障。
 */
public class StubAiService {

    private final HttpServer server;

    private final Endpoint search = new Endpoint();
    private final Endpoint candidates = new Endpoint();
    private final Endpoint qa = new Endpoint();
    private final Endpoint lookup = new Endpoint();
    private final Endpoint titleLookup = new Endpoint();

    /** 一个接口的可配置响应与收到的请求体。 */
    public static final class Endpoint {
        private volatile int status = 200;
        private volatile String body = "{}";
        private final List<String> receivedBodies = new CopyOnWriteArrayList<>();

        public void respondWith(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public List<String> receivedBodies() {
            return List.copyOf(receivedBodies);
        }

        void record(String body) {
            receivedBodies.add(body);
        }

        void reset() {
            receivedBodies.clear();
            respondWith(200, "{}");
        }
    }

    public StubAiService() {
        try {
            // 端口传 0 让系统分配一个空闲端口,避免与其它测试或本机服务撞车
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException("无法启动桩服务", ex);
        }

        // 用守护线程:非守护线程会让 JVM 在测试跑完后不退出,表现为构建挂住
        server.setExecutor(Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "stub-ai-service");
            thread.setDaemon(true);
            return thread;
        }));

        server.createContext("/search", exchange -> handle(exchange, search));
        server.createContext("/recommend/candidates", exchange -> handle(exchange, candidates));
        server.createContext("/qa", exchange -> handle(exchange, qa));
        server.createContext("/lookup/arxiv", exchange -> handle(exchange, lookup));
        // 与 /lookup/arxiv 只差一个后缀 —— HttpServer 按最长前缀选 context,更具体的这个会赢
        server.createContext("/lookup/arxiv-by-title", exchange -> handle(exchange, titleLookup));

        server.start();
    }

    private static void handle(HttpExchange exchange, Endpoint endpoint) throws IOException {
        endpoint.record(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] payload = endpoint.body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(endpoint.status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + getPort();
    }

    /** 检索接口:{@code POST /search} */
    public Endpoint search() {
        return search;
    }

    /** 推荐候选接口:{@code POST /recommend/candidates} */
    public Endpoint candidates() {
        return candidates;
    }

    /** 精读问答接口:{@code POST /qa} */
    public Endpoint qa() {
        return qa;
    }

    /** 按编号取元数据:{@code POST /lookup/arxiv} */
    public Endpoint lookup() {
        return lookup;
    }

    /** 按标题反查预印本:{@code POST /lookup/arxiv-by-title} */
    public Endpoint titleLookup() {
        return titleLookup;
    }

    public void reset() {
        search.reset();
        candidates.reset();
        qa.reset();
        lookup.reset();
        titleLookup.reset();
    }
}
