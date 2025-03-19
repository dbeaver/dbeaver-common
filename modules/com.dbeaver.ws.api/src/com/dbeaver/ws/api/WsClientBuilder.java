/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp
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
package com.dbeaver.ws.api;

import jakarta.websocket.*;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.websocket.jakarta.client.internal.JakartaWebSocketClientContainer;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builder for configuring and creating WebSocketClient instances.
 */
public class WsClientBuilder {
    private static final Logger logger = Logger.getLogger(WsClientBuilder.class.getName());

    private String url;
    private Map<String, String> headers;
    private Duration timeout;

    public WsClientBuilder url(String url) {
        this.url = url;
        return this;
    }

    public WsClientBuilder headers(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public WsClientBuilder timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Builds and connects a WebSocketClient using the given configuration.
     *
     * @return A connected WsClient instance.
     * @throws DeploymentException if the WebSocket deployment fails.
     * @throws IOException         if a connection error occurs.
     */
    public WsClient connect() throws DeploymentException, IOException {
        ClientEndpointConfig config = createEndpointConfig();
        Endpoint endpoint = new WsClientEndpoint(timeout);
        JakartaWebSocketClientContainer clientContainer = new JakartaWebSocketClientContainer((HttpClient) null);
        LifeCycle.start(clientContainer);

        Session session = clientContainer.connectToServer(endpoint, config, URI.create(url));
        WsClient wsClient = new WsClient(session);
        // Store client reference so that the endpoint can signal closure.
        session.getUserProperties().put(WsClient.class.getName(), wsClient);
        return wsClient;
    }

    private ClientEndpointConfig createEndpointConfig() {
        ClientEndpointConfig.Configurator configurator = new ClientEndpointConfig.Configurator() {
            @Override
            public void beforeRequest(Map<String, List<String>> headersMap) {
                if (headers != null) {
                    headers.forEach((key, value) -> headersMap.put(key, List.of(value)));
                }
            }

            @Override
            public void afterResponse(HandshakeResponse response) {
                List<String> handshakeErrors = response.getHeaders().get("X-Handshake-Error");
                if (handshakeErrors != null && !handshakeErrors.isEmpty()) {
                    throw new WsRuntimeException("Handshake error: " + handshakeErrors.get(0));
                }
            }
        };
        return ClientEndpointConfig.Builder.create().configurator(configurator).build();
    }

    private static class WsClientEndpoint extends Endpoint {
        private final Duration timeout;

        public WsClientEndpoint(Duration timeout) {
            this.timeout = timeout;
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.setMaxIdleTimeout(timeout.toMillis());
            session.setMaxTextMessageBufferSize(Integer.MAX_VALUE);
        }

        @Override
        public void onError(Session session, Throwable thr) {
            logger.log(Level.SEVERE, "WebSocket error", thr);
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            logger.log(Level.INFO, "WebSocket closed: " + closeReason);
            WsClient wsClient = (WsClient) session.getUserProperties().get(WsClient.class.getName());
            if (wsClient != null) {
                wsClient.close();
            }
        }
    }
}
