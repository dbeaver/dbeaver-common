/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
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
import org.jkiss.utils.CommonUtils;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles the temporary HTTP server that listens for OAuth callback requests.
 * Extracts the authorization code or error from the query string and returns it to the caller.
 */
public class OAuthCodeResponseHandler implements Closeable {

    private static HttpServer httpServer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final String SUCCESSFUL_ANSWER_FOR_AUTH = "Auth has been completed";
    private static final String FAILED_ANSWER_FOR_AUTH = "Errors encountered during authorization";

    private final int port;
    @NotNull
    private final String callbackEndpoint;

    /**
     * Creates a new instance of the response handler.
     *
     * @param port             the port to listen on
     * @param callbackEndpoint the expected endpoint path (e.g. "/callback")
     */
    public OAuthCodeResponseHandler(int port, @NotNull String callbackEndpoint) {
        this.port = port;
        this.callbackEndpoint = callbackEndpoint;
    }

    /**
     * Initializes the HTTP server on a free port.
     * The server is configured with a thread pool and started immediately.
     *
     * @throws IOException if the server cannot be created or bound to the selected port.
     */
    public void initServer() throws IOException {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 1);
        } catch (IOException e) {
            throw new IOException("Can't create callback server");
        }
        httpServer.setExecutor(new ThreadPoolExecutor(1, 10, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>()));
        httpServer.start();
    }

    /**
     * Submits a request to wait for an OAuth authorization code via HTTP callback.
     * The method creates a temporary HTTP context that listens for a request containing
     * either an authorization code or an error. The code is extracted from the query string.
     *
     * @return a {@link Future} containing the received authorization code, or throws an exception if an error occurred.
     */
    public Future<String> requestCode() {
        return executor.submit(() -> {
            AtomicReference<String> result = new AtomicReference<>();
            AtomicBoolean hasErrors = new AtomicBoolean(false);
            httpServer.createContext(
                callbackEndpoint, exchange -> {
                    String query = exchange.getRequestURI().getQuery();
                    String answer;
                    Map<String, String> params = Stream.of(query.split("&"))
                        .map(kv -> kv.split("=", 2))
                        .collect(Collectors.toMap(kv -> kv[0], kv -> kv[kv.length - 1]));
                    String code = params.get("code");
                    if (CommonUtils.isNotEmpty(code)) {
                        result.set(code);
                        answer = SUCCESSFUL_ANSWER_FOR_AUTH;
                    } else {
                        hasErrors.set(true);
                        String error = params.get("error");
                        if (CommonUtils.isNotEmpty(error)) {
                            result.set(error);
                        }
                        answer = FAILED_ANSWER_FOR_AUTH;
                    }

                    exchange.sendResponseHeaders(200, answer.getBytes().length);
                    exchange.getResponseBody().write(answer.getBytes());
                    exchange.close();
                }
            );
            while (result.get() == null) {
                Thread.onSpinWait();
            }
            httpServer.removeContext(callbackEndpoint);
            if (hasErrors.get()) {
                throw new IOException("Error receiving code " + result.get());
            }
            return result.get();
        });
    }

    /**
     * Adds a temporary HTTP context at the callback endpoint to respond with a 200 OK
     * and immediately removes itself. This is typically used as a stub or placeholder context.
     */
    public void addStabContext() {
        httpServer.createContext(
            callbackEndpoint, exchange -> {
                exchange.sendResponseHeaders(200, 0);
                httpServer.removeContext(callbackEndpoint);
                exchange.close();
            }
        );
    }

    /**
     * Stops the HTTP server and releases resources.
     *
     * @throws IOException if the server fails to stop
     */
    @Override
    public void close() throws IOException {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        executor.shutdown();
    }
}
