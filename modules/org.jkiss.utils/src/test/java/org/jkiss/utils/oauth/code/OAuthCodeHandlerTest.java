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

import com.sun.net.httpserver.HttpServer;
import org.jkiss.code.NotNull;
import org.jkiss.utils.HttpUtils;
import org.jkiss.utils.IOUtils;
import org.jkiss.utils.oauth.OAuthUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class OAuthCodeHandlerTest {
    @Test
    void addsStateToCustomAuthorizationUrl() throws IOException {
        TestOAuthCodeHandler handler = new TestOAuthCodeHandler();

        handler.startSSO(new NoOpResponseHandler());

        assertEquals("https://example.com/authorize?custom=value&state=expected", handler.openedUrl);
    }

    @Test
    void legacyAuthorizeKeepsTokenTransportAndIdTokenMapping() throws Exception {
        AtomicReference<Map<String, String>> posted = new AtomicReference<>();
        HttpServer tokenServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        tokenServer.createContext("/token", exchange -> {
            posted.set(HttpUtils.parseQuery(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] response = "{\"id_token\":\"legacy-identity\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        tokenServer.start();
        AtomicReference<URI> callback = new AtomicReference<>();
        OAuthCodeHandler handler = new OAuthCodeHandler("client", "test-secret", "https://example.test/authorize",
            "http://127.0.0.1:" + tokenServer.getAddress().getPort() + "/token", "/callback", "http://localhost:0/callback", 0) {
            @Override
            protected void createBrowser(@NotNull String url) throws IOException {
                Map<String, String> parameters = HttpUtils.parseQuery(URI.create(url).getRawQuery());
                callback.set(URI.create(parameters.get("redirect_uri")));
                assertEquals(200, send(callback.get() + "?code=auth-code&state=" + parameters.get("state")));
            }
        };
        try {
            assertEquals(Map.of("token", "legacy-identity"), handler.authorize());
            assertEquals("localhost", callback.get().getHost());
            assertTrue(callback.get().getPort() > 0);
            assertEquals(callback.get().toString(), posted.get().get("redirect_uri"));
            assertEquals("test-secret", posted.get().get("client_secret"));
            assertEquals("authorization_code", posted.get().get("grant_type"));
            assertClosed(callback.get());
        } finally {
            tokenServer.stop(0);
        }
    }

    @Test
    void customExchangeReceivesBoundRedirectAndFreshPkceForEveryAttempt() throws Exception {
        AtomicReference<Map<String, String>> parameters = new AtomicReference<>();
        var handler = new LocalOAuthCodeHandler(url -> {
            parameters.set(HttpUtils.parseQuery(URI.create(url).getRawQuery()));
            assertEquals(200, send(parameters.get().get("redirect_uri") + "?code=auth-code&state=" + parameters.get().get("state")));
        });
        AtomicReference<String> previousVerifier = new AtomicReference<>();
        String previousState = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            URI callback = handler.authorize((code, verifier, redirect) -> {
                assertEquals("auth-code", code);
                assertEquals(parameters.get().get("redirect_uri"), redirect);
                assertEquals("S256", parameters.get().get("code_challenge_method"));
                assertEquals(parameters.get().get("code_challenge"), OAuthUtils.generateCodeChallenge(verifier));
                assertNotEquals(previousVerifier.getAndSet(verifier), verifier);
                return URI.create(redirect);
            }, () -> false);
            assertEquals("127.0.0.1", callback.getHost());
            assertTrue(callback.getPort() > 0);
            assertNotEquals(previousState, handler.getState());
            previousState = handler.getState();
            assertClosed(callback);
        }
    }

    @Test
    void cancellationWhileWaitingClosesListenerWithoutExchangingCode() {
        AtomicBoolean canceled = new AtomicBoolean();
        AtomicBoolean exchanged = new AtomicBoolean();
        AtomicReference<URI> callback = new AtomicReference<>();
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var handler = new LocalOAuthCodeHandler(url -> {
            callback.set(URI.create(HttpUtils.parseQuery(URI.create(url).getRawQuery()).get("redirect_uri")));
            scheduler.schedule(() -> canceled.set(true), 150, TimeUnit.MILLISECONDS);
        });
        try {
            assertThrows(InterruptedException.class, () -> handler.authorize((code, verifier, redirect) -> {
                exchanged.set(true);
                return code;
            }, canceled::get));
            assertFalse(exchanged.get());
            assertClosed(callback.get());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void timeoutClosesListener() {
        AtomicReference<URI> callback = new AtomicReference<>();
        var handler = new LocalOAuthCodeHandler(url ->
            callback.set(URI.create(HttpUtils.parseQuery(URI.create(url).getRawQuery()).get("redirect_uri"))));
        handler.setTimeout(1);
        IOException error = assertThrows(IOException.class, () -> handler.authorize((code, verifier, redirect) -> {
            throw new AssertionError("Token exchange must not start after timeout");
        }, () -> false));
        assertInstanceOf(TimeoutException.class, error.getCause());
        assertClosed(callback.get());
    }

    @Test
    void browserFailureClosesListener() {
        AtomicReference<URI> callback = new AtomicReference<>();
        var handler = new LocalOAuthCodeHandler(url -> {
            callback.set(URI.create(HttpUtils.parseQuery(URI.create(url).getRawQuery()).get("redirect_uri")));
            throw new IOException("Browser unavailable");
        });
        assertThrows(IOException.class, () -> handler.authorize((code, verifier, redirect) -> {
            throw new AssertionError("Token exchange must not start when the browser fails");
        }, () -> false));
        assertClosed(callback.get());
    }

    @Test
    void tokenExchangeFailureClosesListener() {
        AtomicReference<URI> callback = new AtomicReference<>();
        var handler = new LocalOAuthCodeHandler(url -> {
            Map<String, String> parameters = HttpUtils.parseQuery(URI.create(url).getRawQuery());
            callback.set(URI.create(parameters.get("redirect_uri")));
            assertEquals(200, send(callback.get() + "?code=auth-code&state=" + parameters.get("state")));
        });
        assertThrows(IOException.class, () -> handler.authorize((code, verifier, redirect) -> {
            throw new IOException("Token endpoint unavailable");
        }, () -> false));
        assertClosed(callback.get());
    }

    @Test
    void externalCallbackHandlersKeepExplicitStateAndRedirect() throws Exception {
        var handler = new OAuthCodeHandler("client", null, "https://example.test/authorize", "https://example.test/token",
            "/callback", "https://application.example.test/callback", 0) {
            {
                state = "external-state";
            }

            @NotNull
            @Override
            protected IOAuthCodeResponseHandler createCodeResponseHandler() {
                return new NoOpResponseHandler() {
                    @NotNull
                    @Override
                    public Future<String> requestCode() {
                        return CompletableFuture.completedFuture("external-code");
                    }
                };
            }

            @Override
            protected void createBrowser(@NotNull String url) {
                Map<String, String> parameters = HttpUtils.parseQuery(URI.create(url).getRawQuery());
                assertEquals("external-state", parameters.get("state"));
                assertEquals("https://application.example.test/callback", parameters.get("redirect_uri"));
            }
        };
        for (int attempt = 0; attempt < 2; attempt++) {
            assertEquals("https://application.example.test/callback", handler.authorize((code, verifier, redirect) -> {
                assertEquals("external-code", code);
                return redirect;
            }, () -> false));
        }
    }

    private static void assertClosed(@NotNull URI callback) {
        assertThrows(IOException.class, () -> send(callback.toString()));
    }

    private static int send(@NotNull String url) throws IOException {
        HttpClient client = HttpClient.newHttpClient();
        try {
            return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } finally {
            IOUtils.tryClose(client);
        }
    }

    @FunctionalInterface
    private interface BrowserOpener {
        void open(@NotNull String url) throws IOException;
    }

    private static class LocalOAuthCodeHandler extends OAuthCodeHandler {
        @NotNull
        private final BrowserOpener browser;

        private LocalOAuthCodeHandler(@NotNull BrowserOpener browser) {
            super("client", null, "https://example.test/authorize", "http://127.0.0.1:1/token",
                "/callback", "http://127.0.0.1:0/callback", 0);
            this.browser = browser;
        }

        @NotNull
        @Override
        protected IOAuthCodeResponseHandler createCodeResponseHandler() {
            return new OAuthCodeResponseHandler(new InetSocketAddress("127.0.0.1", 0), "/callback", getState(), null);
        }

        @Override
        protected void createBrowser(@NotNull String url) throws IOException {
            browser.open(url);
        }
    }

    private static class TestOAuthCodeHandler extends OAuthCodeHandler {
        private String openedUrl;

        private TestOAuthCodeHandler() {
            super(
                "client",
                null,
                "https://example.com/authorize",
                "https://example.com/token",
                "/callback",
                "http://localhost/callback",
                0
            );
            state = "expected";
        }

        @Override
        protected String buildAuthUrl() {
            return "https://example.com/authorize?custom=value";
        }

        @Override
        protected void createBrowser(@NotNull String url) {
            openedUrl = url;
        }
    }

    private static class NoOpResponseHandler implements IOAuthCodeResponseHandler {
        @Override
        public void initServer() {
        }

        @NotNull
        @Override
        public Future<String> requestCode() {
            return new CompletableFuture<>();
        }

        @Override
        public void addStabContext() {
        }

        @Override
        public void close() {
        }
    }
}
