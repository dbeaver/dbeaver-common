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
package org.jkiss.utils.oauth.code;

import org.jkiss.code.NotNull;
import org.jkiss.utils.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class OAuthCodeResponseHandlerTest {
    @Test
    void returnsAuthorizationCode() throws Exception {
        int port = getFreePort();
        try (OAuthCodeResponseHandler handler = new OAuthCodeResponseHandler(port, "/callback", "expected")) {
            handler.initServer();

            HttpResponse<String> response = sendCallback(port, "?code=auth-code&state=expected");

            assertEquals(200, response.statusCode());
            assertEquals("auth-code", handler.requestCode().get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void returnsCustomSuccessHtml() throws Exception {
        int port = getFreePort();
        String successHtml = "<html><body>Authentication complete</body></html>";
        try (OAuthCodeResponseHandler handler =
                 new OAuthCodeResponseHandler(port, "/callback", "expected", successHtml)) {
            handler.initServer();

            HttpResponse<String> response = sendCallback(port, "?code=auth-code&state=expected");

            assertEquals(200, response.statusCode());
            assertEquals("text/html; charset=UTF-8", response.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(successHtml, response.body());
            assertEquals("auth-code", handler.requestCode().get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void ignoresUnexpectedState() throws Exception {
        int port = getFreePort();
        try (OAuthCodeResponseHandler handler = new OAuthCodeResponseHandler(port, "/callback", "expected")) {
            handler.initServer();

            HttpResponse<String> rejected = sendCallback(port, "?code=stale-code&state=unexpected");
            sendCallback(port, "?code=auth-code&state=expected");

            assertEquals(400, rejected.statusCode());
            assertEquals("auth-code", handler.requestCode().get(1, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "127.0.0.1"})
    void callbacksRejectInvalidRequestsWithoutConsumingTheFlow(@NotNull String host) throws Exception {
        try (var handler = createHandler(host)) {
            handler.initServer();
            URI callback = handler.getCallbackUri();
            String code = "a".repeat(4096);
            String validQuery = "?code=" + code + "&state=expected";
            assertEquals(host, callback.getHost());
            assertTrue(callback.getPort() > 0);
            assertEquals(400, sendRaw(callback, "POST", "/callback" + validQuery, callback.getRawAuthority()));
            assertEquals(400, sendRaw(callback, "GET", "/callback/other" + validQuery, callback.getRawAuthority()));
            assertEquals(400, sendRaw(callback, "GET", "/callback" + validQuery, "foreign.example.test"));
            for (String query : new String[] {
                "?code=auth-code&state=wrong",
                validQuery + "&state=wrong",
                validQuery + "&%73tate=wrong",
                "?code=auth-code&state=%"
            }) {
                assertEquals(400, sendRaw(callback, "GET", "/callback" + query, callback.getRawAuthority()));
                assertFalse(handler.requestCode().isDone());
            }
            // HttpUtils retains the last decoded value of a repeated parameter.
            String acceptedQuery = "?code=ignored&state=wrong&code=" + code + "&%73tate=expected";
            assertEquals(200, sendRaw(callback, "GET", "/callback" + acceptedQuery, callback.getRawAuthority()));
            assertEquals(code, handler.requestCode().get(1, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "127.0.0.1"})
    void oauthErrorPreservesStatusAndReportsDescriptionOnlyToCaller(@NotNull String host) throws Exception {
        try (var handler = createHandler(host)) {
            handler.initServer();
            URI callback = handler.getCallbackUri();
            HttpClient client = HttpClient.newHttpClient();
            try {
                var response = client.send(HttpRequest.newBuilder(URI.create(callback
                        + "?error=access_denied&error_description=private-details&state=expected"))
                        .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
                assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElseThrow());
                assertFalse(response.body().contains("private-details"));
                ExecutionException error = assertThrows(ExecutionException.class,
                    () -> handler.requestCode().get(1, TimeUnit.SECONDS));
                assertEquals("Error receiving code private-details", error.getCause().getMessage());
            } finally {
                IOUtils.tryClose(client);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "127.0.0.1"})
    void stubAcknowledgesOneAdditionalRequestWithoutChangingCode(@NotNull String host) throws Exception {
        try (var handler = createHandler(host)) {
            handler.initServer();
            URI callback = handler.getCallbackUri();
            assertEquals(200, sendRaw(callback, "GET", "/callback?code=auth-code&state=expected", callback.getRawAuthority()));
            assertEquals("auth-code", handler.requestCode().get(1, TimeUnit.SECONDS));
            handler.addStabContext();
            assertEquals(200, sendRaw(callback, "GET", "/callback", callback.getRawAuthority()));
            assertEquals(404, sendRaw(callback, "GET", "/callback", callback.getRawAuthority()));
            assertEquals("auth-code", handler.requestCode().get(1, TimeUnit.SECONDS));
        }
    }

    @NotNull
    private static OAuthCodeResponseHandler createHandler(@NotNull String host) {
        return "localhost".equals(host)
            ? new OAuthCodeResponseHandler(0, "/callback", "expected")
            : new OAuthCodeResponseHandler(new InetSocketAddress(host, 0), "/callback", "expected", null);
    }

    @Test
    void ephemeralPortsStayReservedAndNonLoopbackBindingIsRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new OAuthCodeResponseHandler(
            new InetSocketAddress("0.0.0.0", 0), "/callback", "expected", null));
        try (var first = new OAuthCodeResponseHandler(new InetSocketAddress("127.0.0.1", 0), "/callback", "first", null);
             var second = new OAuthCodeResponseHandler(new InetSocketAddress("127.0.0.1", 0), "/callback", "second", null)) {
            first.initServer();
            second.initServer();
            assertNotEquals(first.getCallbackUri().getPort(), second.getCallbackUri().getPort());
        }
    }

    private static int sendRaw(@NotNull URI callback, @NotNull String method, @NotNull String path, @NotNull String host)
        throws Exception {
        try (Socket socket = new Socket(callback.getHost(), callback.getPort())) {
            socket.setSoTimeout(2000);
            socket.getOutputStream().write((method + " " + path + " HTTP/1.1\r\nHost: " + host
                + "\r\nConnection: close\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            String status = reader.readLine();
            reader.transferTo(Writer.nullWriter());
            return Integer.parseInt(status.split(" ")[1]);
        }
    }

    private static HttpResponse<String> sendCallback(int port, String query) throws Exception {
        return HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/callback" + query)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private static int getFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
