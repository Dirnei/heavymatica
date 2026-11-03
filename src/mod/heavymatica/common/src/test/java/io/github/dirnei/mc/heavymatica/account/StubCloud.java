package io.github.dirnei.mc.heavymatica.account;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** A minimal HTTP server that answers with queued responses and records every request it receives. */
final class StubCloud implements AutoCloseable {

    record Request(String method, String path, String authorization, String contentType, byte[] body) {
        String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    record Response(int status, String contentType, byte[] body) {
    }

    private final HttpServer server;
    private final Deque<Response> responses = new ArrayDeque<>();
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());

    StubCloud() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    StubCloud answer(int status, String json) {
        synchronized (responses) {
            responses.add(new Response(status, "application/json", json.getBytes(StandardCharsets.UTF_8)));
        }
        return this;
    }

    StubCloud problem(int status, String code) {
        synchronized (responses) {
            responses.add(new Response(status, "application/problem+json",
                    ("{\"code\":\"" + code + "\",\"title\":\"Problem " + code + "\",\"status\":" + status + "}").getBytes(StandardCharsets.UTF_8)));
        }
        return this;
    }

    StubCloud answerBytes(int status, byte[] body) {
        synchronized (responses) {
            responses.add(new Response(status, "application/octet-stream", body));
        }
        return this;
    }

    List<Request> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestHeaders().getFirst("Content-Type"), body));
        Response response;
        synchronized (responses) {
            response = responses.isEmpty() ? new Response(500, "text/plain", "no response queued".getBytes(StandardCharsets.UTF_8)) : responses.poll();
        }
        byte[] bytes = response.body();
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        if (response.status() == 204) {
            exchange.sendResponseHeaders(204, -1);
        } else {
            exchange.sendResponseHeaders(response.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
