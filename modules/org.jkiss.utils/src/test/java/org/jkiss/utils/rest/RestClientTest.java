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
package org.jkiss.utils.rest;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestClientTest {
    private final BlockingQueue<String> requests = new LinkedBlockingQueue<>();
    private HttpServer server;
    private TestService client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", exchange -> {
            try {
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
                requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = "\"done\"".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally {
                exchange.close();
            }
        });
        server.start();
        client = RestClient.builder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api"),
            TestService.class).create();
    }

    @AfterEach
    void stopServer() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsUnannotatedArgumentInPositionalWrapper() throws Exception {
        client.send(new Payload("example", null));
        assertEquals("POST /api/body", nextRequest());
        assertEquals(JsonParser.parseString("{\"arg0\":{\"name\":\"example\",\"description\":null}}"),
            JsonParser.parseString(nextRequest()));
    }

    @Test
    void supportsNullPositionalArgument() throws Exception {
        client.send(null);
        assertEquals("POST /api/body", nextRequest());
        assertEquals(JsonParser.parseString("{\"arg0\":null}"), JsonParser.parseString(nextRequest()));
    }

    @Test
    void preservesNamedParametersAndResponseDeserialization() throws Exception {
        assertEquals("done", client.named(new Payload("example", "details"), 2));
        assertEquals("POST /api/named", nextRequest());
        assertEquals(JsonParser.parseString("{\"payload\":{\"name\":\"example\",\"description\":\"details\"},\"count\":2}"),
            JsonParser.parseString(nextRequest()));
    }

    @Test
    void preservesSingleNamedParameterWrapper() throws Exception {
        client.wrapped(new Payload("example", null));
        assertEquals("POST /api/wrapped", nextRequest());
        assertEquals(JsonParser.parseString("{\"payload\":{\"name\":\"example\",\"description\":null}}"),
            JsonParser.parseString(nextRequest()));
    }

    @Test
    void preservesParameterlessRequests() throws Exception {
        client.empty();
        assertEquals("POST /api/empty", nextRequest());
        assertEquals("{}", nextRequest());
    }

    @Test
    void closesClientWithoutLifecycleMethods() throws Exception {
        var service = RestClient.builder(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api"), PlainService.class
        ).create();
        try {
            assertEquals("done", service.call());
            assertEquals("POST /api/call", nextRequest());
            assertEquals("{}", nextRequest());
            RestClient.close(service);
            assertThrows(RpcException.class, service::call);
            assertTrue(requests.isEmpty());
        } finally {
            // Closing an already closed client is safe.
            RestClient.close(service);
        }
    }

    @Test
    void serializesMultipleUnannotatedArgumentsByPosition() throws Exception {
        client.positional(new Payload("example", null), 2);
        assertEquals("POST /api/positional", nextRequest());
        assertEquals(JsonParser.parseString("{\"arg0\":{\"name\":\"example\",\"description\":null},\"arg1\":2}"),
            JsonParser.parseString(nextRequest()));
    }

    @NotNull
    private String nextRequest() throws InterruptedException {
        String request = requests.poll(5, TimeUnit.SECONDS);
        assertNotNull(request);
        return request;
    }

    public record Payload(@NotNull String name, @Nullable String description) {
    }

    public interface PlainService {
        @NotNull
        String call();
    }

    public interface TestService extends AutoCloseable {
        @RequestMapping("body")
        void send(@Nullable Payload payload);

        @NotNull
        String named(@RequestParameter("payload") @NotNull Payload payload, @RequestParameter("count") int count);

        void wrapped(@RequestParameter("payload") @NotNull Payload payload);

        void empty();

        void positional(@NotNull Payload payload, int count);

        @Override
        void close();
    }
}
