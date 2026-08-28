package com.example.partners.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Petit serveur HTTP de test (JDK pur) qui rejoue une file de réponses programmées
 * et compte les requêtes reçues. Permet de tester retry/circuit breaker sans mock
 * du transport HTTP.
 */
public class StubHttpServer implements AutoCloseable {

    public record Response(int status, String body) {
        public static Response of(int status, String body) {
            return new Response(status, body);
        }
    }

    private final HttpServer server;
    private final Deque<Response> queue = new ArrayDeque<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile Response defaultResponse = new Response(200, "{}");
    private volatile long delayMillis = 0;

    public StubHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true); // évite de bloquer l'arrêt de la JVM sur des requêtes en attente (hangResponses)
            return t;
        }));
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Response response;
            synchronized (queue) {
                response = queue.isEmpty() ? defaultResponse : queue.poll();
            }
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    public void enqueue(int status, String body) {
        synchronized (queue) {
            queue.add(new Response(status, body));
        }
    }

    public void setDefaultResponse(int status, String body) {
        this.defaultResponse = new Response(status, body);
    }

    /** Simule un serveur qui ne répond jamais dans le délai imparti (timeout côté client). */
    public void hangResponses(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    public int requestCount() {
        return requestCount.get();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
