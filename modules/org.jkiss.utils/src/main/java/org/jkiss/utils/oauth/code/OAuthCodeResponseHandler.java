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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.HttpConstants;
import org.jkiss.utils.HttpUtils;
import org.jkiss.utils.oauth.OAuthConstants;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Handles the temporary HTTP server that listens for OAuth callback requests.
 * Extracts the authorization code or error from the query string and returns it to the caller.
 */
public class OAuthCodeResponseHandler implements IOAuthCodeResponseHandler {
    private static final String SUCCESSFUL_ANSWER_FOR_AUTH = "Auth has been completed";
    private static final String FAILED_ANSWER_FOR_AUTH = "Errors encountered during authorization";

    @NotNull
    private final InetSocketAddress bindAddress;
    @NotNull
    private final String callbackEndpoint;
    @Nullable
    private final String expectedState;
    @Nullable
    private final String successHtml;
    @NotNull
    private final CompletableFuture<String> authorizationCode = new CompletableFuture<>();
    @NotNull
    private final ThreadPoolExecutor serverExecutor;

    @Nullable
    private HttpServer httpServer;

    /**
     * Creates a new instance of the response handler.
     *
     * @param port             the port to listen on
     * @param callbackEndpoint the expected endpoint path (e.g. "/callback")
     */
    public OAuthCodeResponseHandler(int port, @NotNull String callbackEndpoint) {
        this(port, callbackEndpoint, null);
    }

    /**
     * Creates a response handler that validates the state returned by the authorization server.
     */
    public OAuthCodeResponseHandler(int port, @NotNull String callbackEndpoint, @Nullable String expectedState) {
        this(port, callbackEndpoint, expectedState, null);
    }

    /**
     * Creates a response handler that returns the supplied HTML after successful authorization.
     */
    public OAuthCodeResponseHandler(
        int port,
        @NotNull String callbackEndpoint,
        @Nullable String expectedState,
        @Nullable String successHtml
    ) {
        this(new InetSocketAddress("localhost", port), callbackEndpoint, expectedState, successHtml);
    }

    /** Creates a loopback callback listener; port zero reserves an ephemeral port until close(). */
    public OAuthCodeResponseHandler(
        @NotNull InetSocketAddress bindAddress,
        @NotNull String callbackEndpoint,
        @Nullable String expectedState,
        @Nullable String successHtml
    ) {
        if (bindAddress.getAddress() == null || !bindAddress.getAddress().isLoopbackAddress()) {
            throw new IllegalArgumentException("OAuth callback must bind to a loopback address");
        }
        this.bindAddress = bindAddress;
        this.callbackEndpoint = callbackEndpoint;
        this.expectedState = expectedState;
        this.successHtml = successHtml;
        this.serverExecutor = new ThreadPoolExecutor(
            1,
            10,
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>()
        );
    }

    /**
     * Initializes the callback server on the loopback interface.
     *
     * @throws IOException if the server cannot be created or bound to the selected port
     */
    @Override
    public void initServer() throws IOException {
        try {
            httpServer = HttpServer.create(bindAddress, 1);
        } catch (IOException e) {
            throw new IOException("Can't create callback server", e);
        }
        httpServer.setExecutor(serverExecutor);
        httpServer.createContext(callbackEndpoint, exchange -> {
            try {
                receive(exchange);
            } finally {
                exchange.close();
            }
        });
        httpServer.start();
    }

    private void receive(@NotNull HttpExchange exchange) throws IOException {
        if (!HttpConstants.METHOD_GET.equals(exchange.getRequestMethod())
            || !getCallbackUri().getRawPath().equals(exchange.getRequestURI().getRawPath())
            || !getCallbackUri().getRawAuthority().equals(exchange.getRequestHeaders().getFirst(HttpConstants.HEADER_HOST))) {
            exchange.sendResponseHeaders(HttpConstants.CODE_BAD_REQUEST, -1);
            return;
        }
        Map<String, String> params = HttpUtils.parseQuery(exchange.getRequestURI().getRawQuery());
        if (!isValidCallbackParameters(params)) {
            exchange.sendResponseHeaders(HttpConstants.CODE_BAD_REQUEST, -1);
            return;
        }
        String code = params.get(OAuthConstants.PARAM_CODE);
        String error = params.get(OAuthConstants.PARAM_ERROR);
        String answer;
        int statusCode;
        String receivedCode = null;
        IOException failure = null;
        if (expectedState != null && !expectedState.equals(params.get(OAuthConstants.PARAM_STATE))) {
            answer = FAILED_ANSWER_FOR_AUTH;
            statusCode = HttpConstants.CODE_BAD_REQUEST;
        } else if (CommonUtils.isNotEmpty(error)) {
            httpServer.removeContext(callbackEndpoint);
            String description = params.get(OAuthConstants.PARAM_ERROR_DESCRIPTION);
            failure = new IOException("Error receiving code " + (CommonUtils.isNotEmpty(description) ? description : error));
            answer = FAILED_ANSWER_FOR_AUTH;
            statusCode = HttpConstants.CODE_OK;
        } else if (CommonUtils.isNotEmpty(code)) {
            httpServer.removeContext(callbackEndpoint);
            receivedCode = code;
            answer = successHtml == null ? SUCCESSFUL_ANSWER_FOR_AUTH : successHtml;
            statusCode = HttpConstants.CODE_OK;
        } else {
            httpServer.removeContext(callbackEndpoint);
            failure = new IOException("OAuth callback does not contain authorization code");
            answer = FAILED_ANSWER_FOR_AUTH;
            statusCode = HttpConstants.CODE_BAD_REQUEST;
        }

        byte[] response = answer.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
            HttpConstants.HEADER_CONTENT_TYPE,
            successHtml != null && receivedCode != null
                ? HttpConstants.CONTENT_TYPE_TEXT_HTML + "; charset=UTF-8"
                : HttpConstants.CONTENT_TYPE_TEXT_PLAIN + "; charset=UTF-8"
        );
        exchange.getResponseHeaders().set(HttpConstants.HEADER_CACHE_CONTROL, HttpConstants.CACHE_CONTROL_NO_STORE);
        exchange.getResponseHeaders().set(HttpConstants.HEADER_REFERRER_POLICY, HttpConstants.REFERRER_POLICY_NO_REFERRER);
        exchange.sendResponseHeaders(statusCode, response.length);
        try (var body = exchange.getResponseBody()) {
            body.write(response);
        }
        if (receivedCode != null) {
            authorizationCode.complete(receivedCode);
        } else if (failure != null) {
            authorizationCode.completeExceptionally(failure);
        }
    }

    /** Allows a caller to enforce provider-specific callback parameters without owning an HTTP server. */
    protected boolean isValidCallbackParameters(@NotNull Map<String, String> parameters) {
        return true;
    }

    @NotNull
    @Override
    public URI getCallbackUri() throws IOException {
        if (httpServer == null) {
            throw new IOException("OAuth callback server is not initialized");
        }
        try {
            return new URI("http", null, bindAddress.getHostString(), httpServer.getAddress().getPort(), callbackEndpoint, null, null);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid OAuth callback URI", e);
        }
    }

    @NotNull
    @Override
    public Future<String> requestCode() {
        return authorizationCode;
    }

    @Override
    public void addStabContext() {
        if (httpServer == null) {
            return;
        }
        httpServer.createContext(callbackEndpoint, exchange -> {
            exchange.sendResponseHeaders(HttpConstants.CODE_OK, 0);
            httpServer.removeContext(callbackEndpoint);
            exchange.close();
        });
    }

    @Override
    public void close() {
        authorizationCode.cancel(false);
        if (httpServer != null) {
            httpServer.stop(0);
        }
        serverExecutor.shutdownNow();
    }
}
