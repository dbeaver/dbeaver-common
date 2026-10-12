/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2026 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dbeaver.rest.client;

import com.dbeaver.rest.client.interceptor.HttpInterceptor;
import com.sun.net.httpserver.HttpServer;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class AbstractRestClientTest {
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> customHeader = new AtomicReference<>();
    private final AtomicInteger redirectTargets = new AtomicInteger();
    private HttpServer server;
    private String url;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        url = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/form", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            customHeader.set(exchange.getRequestHeaders().getFirst("X-Test"));
            query.set(exchange.getRequestURI().getRawQuery());
            method.set(exchange.getRequestMethod());
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", url + "/target");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            redirectTargets.incrementAndGet();
            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void formPostsUseTheSharedSerializationAndInterceptorChain() throws Exception {
        var client = new Client(url, List.of(chain -> chain.proceed(chain.request().withHeader("X-Test", "intercepted"))));
        Map<String, Object> fields = new HashMap<>();
        fields.put("code", "a+b/% &");
        fields.put("name", "данные");
        fields.put("empty", "");
        fields.put("omitted", null);
        Assertions.assertTrue(client.form(fields).ok());
        Assertions.assertEquals("POST", method.get());
        Assertions.assertNull(query.get());
        Assertions.assertEquals("application/x-www-form-urlencoded", contentType.get());
        Assertions.assertEquals("intercepted", customHeader.get());
        Map<String, String> decoded = new HashMap<>();
        for (String item : receivedBody.get().split("&")) {
            String[] pair = item.split("=", 2);
            decoded.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        Assertions.assertEquals(Map.of("code", "a+b/% &", "name", "данные", "empty", ""), decoded);
    }

    @Test
    void redirectsCanBeDisabledWithoutChangingTheDefaultPolicy() throws Exception {
        Assertions.assertThrows(DBException.class, () -> new Client(url, List.of()).redirect());
        Assertions.assertEquals(0, redirectTargets.get());
        Assertions.assertEquals("ok", new Client(url).redirect());
        Assertions.assertEquals(1, redirectTargets.get());
    }

    private record Result(boolean ok) {
    }

    private static class Client extends AbstractRestClient {
        Client(@NotNull String url) {
            super(url, List.of());
        }

        Client(@NotNull String url, @NotNull List<HttpInterceptor> interceptors) {
            super(url, DEFAULT_CONNECT_TIMEOUT, 5000, interceptors, HttpClient.Redirect.NEVER);
        }

        @NotNull
        String redirect() throws DBException {
            return executeGetRequest("redirect", String.class);
        }

        @NotNull
        Result form(@NotNull Map<String, Object> fields) throws DBException {
            return executePostRequest("form", Map.of(), fields, MediaType.FORM_URLENCODED, Result.class);
        }
    }
}
