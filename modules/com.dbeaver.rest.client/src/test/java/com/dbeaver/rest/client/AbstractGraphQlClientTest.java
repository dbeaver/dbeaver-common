/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2026 DBeaver Corp
 *
 * All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of DBeaver Corp and its suppliers, if any.
 * The intellectual and technical concepts contained
 * herein are proprietary to DBeaver Corp and its suppliers
 * and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from DBeaver Corp.
 */
package com.dbeaver.rest.client;

import com.sun.net.httpserver.HttpServer;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class AbstractGraphQlClientTest {
    private final AtomicReference<String> response = new AtomicReference<>();
    private final AtomicReference<String> request = new AtomicReference<>();
    private HttpServer server;
    private Client client;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        client = new Client("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsTypedDataAndSendsVariables() throws DBException {
        response.set("""
            {"data":{"item":{"name":"Desktop"}}}
            """);
        Assertions.assertEquals("Desktop", client.read(Map.of("id", "dam-1")).item().name());
        Assertions.assertTrue(request.get().contains("\"query\""));
        Assertions.assertTrue(request.get().contains("\"id\": \"dam-1\""));
    }

    @Test
    void sendsQueriesWithoutVariables() throws DBException {
        response.set("""
            {"data":{"item":{"name":"Desktop"}}}
            """);
        Assertions.assertEquals("Desktop", client.read().item().name());
        Assertions.assertFalse(request.get().contains("\"variables\""));
    }

    @Test
    void preservesLargeIntegerInTypedResponse() throws DBException {
        response.set("""
            {"data":{"count":9007199254740993}}
            """);
        Assertions.assertEquals(9007199254740993L, client.readCount().count());
    }

    @Test
    void rejectsPartialDataWhenGraphQlReportsAnError() {
        response.set("""
            {"data":{"item":{"name":"Desktop"}},"errors":[{"message":"Access denied"}]}
            """);
        DBException error = Assertions.assertThrows(DBException.class, () -> client.read(Map.of()));
        Assertions.assertEquals("Could not read item: Access denied", error.getMessage());
    }

    @Test
    void rejectsMissingData() {
        response.set("""
            {"data":null}
            """);
        DBException error = Assertions.assertThrows(DBException.class, () -> client.read(Map.of()));
        Assertions.assertEquals("Could not read item", error.getMessage());
    }

    private record Item(@NotNull String name) {
    }

    private record ItemData(@NotNull Item item) {
    }

    private record CountData(long count) {
    }

    private static class Client extends AbstractGraphQlClient {
        Client(@NotNull String url) {
            super(url, DEFAULT_CONNECT_TIMEOUT, 5000, List.of(), HttpClient.Redirect.NEVER);
        }

        @NotNull
        ItemData read(@NotNull Map<String, ?> variables) throws DBException {
            return executeGraphQl("query($id: ID) { item(id: $id) { name } }", variables, ItemData.class, "Could not read item");
        }

        @NotNull
        ItemData read() throws DBException {
            return executeGraphQl("query { item { name } }", ItemData.class, "Could not read item");
        }

        @NotNull
        CountData readCount() throws DBException {
            return executeGraphQl("query { count }", CountData.class, "Could not read count");
        }
    }
}
