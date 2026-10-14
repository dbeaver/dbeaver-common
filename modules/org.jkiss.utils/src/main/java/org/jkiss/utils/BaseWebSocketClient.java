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
package org.jkiss.utils;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class BaseWebSocketClient implements AutoCloseable {
    private final HttpClient client;
    private final Set<WebSocket> sockets = ConcurrentHashMap.newKeySet();

    public BaseWebSocketClient() {
        this(true);
    }

    public BaseWebSocketClient(boolean useProxy) {
        HttpClient.Builder builder = HttpClient.newBuilder();
        if (useProxy) {
            WSClientUtils.ProxyInfo proxy = WSClientUtils.findProxyInfo();
            if (proxy.exists()) {
                builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));
            }
        }
        client = builder.build();
    }

    public WebSocket connect(URI uri, Map<String, String> headers, Listener listener)
        throws ExecutionException, InterruptedException {
        WebSocket.Builder builder = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(30));
        headers.forEach(builder::header);
        listener.onTermination = sockets::remove;
        WebSocket socket = builder.buildAsync(uri, listener).get();
        if (listener.isOpen()) {
            sockets.add(socket);
        }
        return socket;
    }

    @Override
    public void close() {
        sockets.forEach(WebSocket::abort);
        sockets.clear();
    }

    public abstract static class Listener implements WebSocket.Listener {
        private final StringBuilder text = new StringBuilder();
        private ByteBuffer binary = ByteBuffer.allocate(0);
        private volatile boolean closed;
        private volatile WebSocket activeSocket;
        private volatile long lastActivity;
        private Duration idleTimeout;
        private int maxBinaryMessageSize = Integer.MAX_VALUE;
        private Consumer<WebSocket> onTermination = socket -> {};

        public void setIdleTimeout(Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public void setMaxBinaryMessageSize(int maxBinaryMessageSize) {
            this.maxBinaryMessageSize = maxBinaryMessageSize;
        }

        @Override
        public void onOpen(WebSocket socket) {
            activeSocket = socket;
            closed = false;
            text.setLength(0);
            binary = ByteBuffer.allocate(0);
            lastActivity = System.nanoTime();
            if (idleTimeout != null) {
                checkIdle(socket);
            }
            socket.request(1);
        }

        private void checkIdle(WebSocket socket) {
            long remaining = Math.max(1, idleTimeout.toNanos() - (System.nanoTime() - lastActivity));
            CompletableFuture.delayedExecutor(remaining, TimeUnit.NANOSECONDS).execute(() -> {
                if (!closed && activeSocket == socket) {
                    if (System.nanoTime() - lastActivity >= idleTimeout.toNanos()) {
                        close(socket);
                    } else {
                        checkIdle(socket);
                    }
                }
            });
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            if (activeSocket != socket) {
                return null;
            }
            lastActivity = System.nanoTime();
            text.append(data);
            if (last) {
                String message = text.toString();
                text.setLength(0);
                text(message);
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
            if (activeSocket != socket) {
                return null;
            }
            lastActivity = System.nanoTime();
            if (data.remaining() > maxBinaryMessageSize - binary.remaining()) {
                throw new IllegalStateException("WebSocket binary message exceeds the configured limit");
            }
            if (binary.remaining() == 0 && last) {
                binary(data);
            } else {
                ByteBuffer message = ByteBuffer.allocate(binary.remaining() + data.remaining());
                message.put(binary).put(data).flip();
                binary = message;
                if (last) {
                    binary(binary);
                    binary = ByteBuffer.allocate(0);
                }
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket socket, ByteBuffer message) {
            if (activeSocket == socket) {
                lastActivity = System.nanoTime();
                socket.request(1);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket socket, ByteBuffer message) {
            if (activeSocket == socket) {
                lastActivity = System.nanoTime();
                socket.request(1);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            onTermination.accept(socket);
            if (activeSocket == socket && !closed) {
                closed = true;
                closed(statusCode, reason);
            }
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            onTermination.accept(socket);
            if (activeSocket == socket && !closed) {
                closed = true;
                error(error);
            }
        }

        public boolean isOpen() {
            return !closed;
        }

        public void close(WebSocket socket) {
            if (!closed) {
                closed = true;
                try {
                    socket.sendClose(WebSocket.NORMAL_CLOSURE, "");
                } finally {
                    closed(WebSocket.NORMAL_CLOSURE, "");
                }
            }
        }

        protected void text(String message) {
        }

        protected void binary(ByteBuffer message) {
        }

        protected void closed(int statusCode, String reason) {
        }

        protected void error(Throwable cause) {
        }
    }
}
