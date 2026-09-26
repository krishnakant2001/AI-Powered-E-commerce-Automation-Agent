package com.strikerkk.aicommerce.agent_service.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A tiny in-process stand-in for the Anthropic Messages API.
 * <p>
 * {@code AgentChatService} builds its own {@link java.net.http.HttpClient} inside
 * {@code callLlmApi(...)}, so it cannot be mocked - the only honest way to drive it is to
 * point {@code anthropic.url} at a real socket. This server is that socket.
 */
public final class StubLlmServer implements AutoCloseable {

    public static final String PATH = "/v1/messages";

    private final HttpServer server;

    /** Responses are handed out in order, then {@link #defaultResponse} takes over. */
    private final Queue<String> responses = new ConcurrentLinkedQueue<>();
    private final AtomicReference<String> defaultResponse = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger callCount = new AtomicInteger();

    private final List<String> requestBodies = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> lastHeaders = new ConcurrentHashMap<>();

    private StubLlmServer(HttpServer server) {
        this.server = server;
    }

    public static StubLlmServer start() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            StubLlmServer stub = new StubLlmServer(httpServer);

            httpServer.createContext(PATH, exchange -> {
                stub.callCount.incrementAndGet();
                stub.requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

                exchange.getRequestHeaders().forEach((name, values) -> {
                    if (!values.isEmpty()) {
                        stub.lastHeaders.put(name.toLowerCase(), values.get(0));
                    }
                });

                String payload = stub.responses.poll();
                if (payload == null) {
                    payload = stub.defaultResponse.get();
                }
                if (payload == null) {
                    payload = "{}";
                }

                byte[] out = payload.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(stub.status.get(), out.length);

                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(out);
                }
            });

            httpServer.setExecutor(null);
            httpServer.start();

            return stub;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start the stub LLM server", ex);
        }
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + PATH;
    }

    /** Queue the next answer(s). */
    public StubLlmServer willReturn(String... payloads) {
        Collections.addAll(responses, payloads);
        return this;
    }

    /** Answer used once the queue runs dry - handy for the "runaway tool loop" test. */
    public StubLlmServer willAlwaysReturn(String payload) {
        defaultResponse.set(payload);
        return this;
    }

    public StubLlmServer withStatus(int httpStatus) {
        status.set(httpStatus);
        return this;
    }

    public int callCount() {
        return callCount.get();
    }

    public List<String> requestBodies() {
        return List.copyOf(requestBodies);
    }

    public String lastRequestBody() {
        return requestBodies.isEmpty() ? null : requestBodies.get(requestBodies.size() - 1);
    }

    public String header(String name) {
        return lastHeaders.get(name.toLowerCase());
    }

    public void reset() {
        responses.clear();
        defaultResponse.set(null);
        status.set(200);
        callCount.set(0);
        requestBodies.clear();
        lastHeaders.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

