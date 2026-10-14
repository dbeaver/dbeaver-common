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

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class BaseWebSocketClient implements AutoCloseable {
    private static final long CLOSE_TIMEOUT_SECONDS = 5;
    private static final ProxySelector DIRECT_PROXY = new ProxySelector() {
        @Override
        @NotNull
        public List<Proxy> select(@NotNull URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(@NotNull URI uri, @NotNull SocketAddress address, @NotNull IOException error) {
        }
    };

    private final HttpClient client;
    private final Map<WebSocket, Listener> sockets = new HashMap<>();
    private final Set<Attempt> pending = new HashSet<>();
    private boolean closed;

    public BaseWebSocketClient() {
        this(true);
    }

    public BaseWebSocketClient(boolean useProxy) {
        HttpClient.Builder builder = HttpClient.newBuilder();
        if (useProxy) {
            WSClientUtils.ProxyInfo proxy = WSClientUtils.findProxyInfo();
            if (proxy.exists()) {
                builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));
            } else {
                builder.proxy(DIRECT_PROXY);
            }
        } else {
            builder.proxy(DIRECT_PROXY);
        }
        client = builder.build();
    }

    @NotNull
    public WebSocket connect(
        @NotNull URI uri,
        @NotNull Map<String, String> headers,
        @NotNull Listener listener
    ) throws ExecutionException, InterruptedException {
        Attempt attempt = new Attempt(this, listener);
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("WebSocket client is closed");
            }
            pending.add(attempt);
        }
        CompletableFuture<WebSocket> handshake;
        try {
            WebSocket.Builder builder = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(30));
            headers.forEach(builder::header);
            handshake = builder.buildAsync(uri, attempt);
        } catch (RuntimeException e) {
            synchronized (this) {
                pending.remove(attempt);
            }
            throw e;
        }
        attempt.handshake = handshake;
        if (attempt.cancelled) {
            handshake.cancel(true);
        }
        handshake.whenComplete((socket, error) -> {
            synchronized (this) {
                pending.remove(attempt);
            }
            if (socket != null && attempt.cancelled) {
                listener.abort(socket);
            }
        });
        try {
            WebSocket socket = handshake.get();
            if (attempt.cancelled || !listener.isOpen(socket)) {
                listener.abort(socket);
                throw new ExecutionException(new IOException("WebSocket connection closed during handshake"));
            }
            return socket;
        } catch (InterruptedException e) {
            attempt.cancelled = true;
            handshake.cancel(true);
            WebSocket socket = attempt.socket;
            if (socket != null) {
                listener.abort(socket);
            }
            throw e;
        }
    }

    private synchronized boolean register(@NotNull Attempt attempt, @NotNull WebSocket socket, @NotNull Listener listener) {
        attempt.socket = socket;
        if (closed || attempt.cancelled) {
            return false;
        }
        sockets.put(socket, listener);
        return true;
    }

    private synchronized void unregister(@NotNull WebSocket socket) {
        sockets.remove(socket);
    }

    @Override
    public void close() {
        List<Map.Entry<WebSocket, Listener>> connections;
        List<Attempt> handshakes;
        synchronized (this) {
            closed = true;
            connections = new ArrayList<>(sockets.entrySet());
            handshakes = new ArrayList<>(pending);
        }
        for (Attempt attempt : handshakes) {
            attempt.cancelled = true;
            CompletableFuture<WebSocket> handshake = attempt.handshake;
            if (handshake != null) {
                handshake.cancel(true);
            }
            if (attempt.socket != null) {
                attempt.listener.abort(attempt.socket);
            }
        }
        for (Map.Entry<WebSocket, Listener> entry : connections) {
            entry.getValue().abort(entry.getKey());
        }
    }

    private static final class Attempt implements WebSocket.Listener {
        private final BaseWebSocketClient owner;
        private final Listener listener;
        @Nullable
        private volatile CompletableFuture<WebSocket> handshake;
        @Nullable
        private volatile WebSocket socket;
        private volatile boolean cancelled;

        private Attempt(@NotNull BaseWebSocketClient owner, @NotNull Listener listener) {
            this.owner = owner;
            this.listener = listener;
        }

        @Override
        public void onOpen(@NotNull WebSocket socket) {
            if (cancelled) {
                socket.abort();
                return;
            }
            listener.opening.set(this);
            try {
                listener.onOpen(socket);
            } finally {
                listener.opening.remove();
            }
        }

        @Override
        @Nullable
        public CompletionStage<?> onText(@NotNull WebSocket socket, @NotNull CharSequence data, boolean last) {
            return listener.onText(socket, data, last);
        }

        @Override
        @Nullable
        public CompletionStage<?> onBinary(@NotNull WebSocket socket, @NotNull ByteBuffer data, boolean last) {
            return listener.onBinary(socket, data, last);
        }

        @Override
        @Nullable
        public CompletionStage<?> onPing(@NotNull WebSocket socket, @NotNull ByteBuffer message) {
            return listener.onPing(socket, message);
        }

        @Override
        @Nullable
        public CompletionStage<?> onPong(@NotNull WebSocket socket, @NotNull ByteBuffer message) {
            return listener.onPong(socket, message);
        }

        @Override
        @Nullable
        public CompletionStage<?> onClose(@NotNull WebSocket socket, int statusCode, @NotNull String reason) {
            return listener.onClose(socket, statusCode, reason);
        }

        @Override
        public void onError(@NotNull WebSocket socket, @NotNull Throwable error) {
            listener.onError(socket, error);
        }
    }

    public abstract static class Listener implements WebSocket.Listener {
        private final Map<WebSocket, Connection> connections = java.util.Collections.synchronizedMap(new IdentityHashMap<>());
        private final ThreadLocal<Attempt> opening = new ThreadLocal<>();
        @Nullable
        private volatile Connection active;
        @Nullable
        private Duration idleTimeout;
        private int maxBinaryMessageSize = Integer.MAX_VALUE;

        private static final class Connection {
            private final WebSocket socket;
            @Nullable
            private final BaseWebSocketClient owner;
            private final StringBuilder text = new StringBuilder();
            private ByteBuffer binary = ByteBuffer.allocate(0);
            private volatile long lastActivity = System.nanoTime();
            private volatile boolean closed;
            private boolean notified;

            private Connection(@NotNull WebSocket socket, @Nullable BaseWebSocketClient owner) {
                this.socket = socket;
                this.owner = owner;
            }
        }

        public void setIdleTimeout(@Nullable Duration idleTimeout) {
            this.idleTimeout = idleTimeout != null && idleTimeout.toMillis() > 0 ? idleTimeout : null;
        }

        public void setMaxBinaryMessageSize(int maxBinaryMessageSize) {
            this.maxBinaryMessageSize = maxBinaryMessageSize;
        }

        @Override
        public void onOpen(@NotNull WebSocket socket) {
            open(socket, opening.get());
        }

        private void open(@NotNull WebSocket socket, @Nullable Attempt attempt) {
            Connection connection = new Connection(socket, attempt == null ? null : attempt.owner);
            connections.put(socket, connection);
            active = connection;
            if (attempt != null && !attempt.owner.register(attempt, socket, this)) {
                abort(socket);
                return;
            }
            if (idleTimeout != null) {
                checkIdle(connection);
            }
            socket.request(1);
        }

        private void checkIdle(@NotNull Connection connection) {
            long remaining = Math.max(1, idleTimeout.toNanos() - (System.nanoTime() - connection.lastActivity));
            CompletableFuture.delayedExecutor(remaining, TimeUnit.NANOSECONDS).execute(() -> {
                boolean expired;
                synchronized (connection) {
                    if (connection.closed) {
                        return;
                    }
                    expired = System.nanoTime() - connection.lastActivity >= idleTimeout.toNanos();
                    if (expired) {
                        connection.closed = true;
                    }
                }
                if (expired) {
                    startClose(connection);
                } else {
                    checkIdle(connection);
                }
            });
        }

        public void activity(@NotNull WebSocket socket) {
            Connection connection = connections.get(socket);
            if (connection != null) {
                synchronized (connection) {
                    if (!connection.closed) {
                        connection.lastActivity = System.nanoTime();
                    }
                }
            }
        }

        @Override
        @Nullable
        public CompletionStage<?> onText(@NotNull WebSocket socket, @NotNull CharSequence data, boolean last) {
            Connection connection = connections.get(socket);
            if (connection == null || connection.closed) {
                return null;
            }
            activity(socket);
            connection.text.append(data);
            if (last) {
                String message = connection.text.toString();
                connection.text.setLength(0);
                if (active == connection && !connection.closed) {
                    text(message);
                }
            }
            if (!connection.closed) {
                socket.request(1);
            }
            return null;
        }

        @Override
        @Nullable
        public CompletionStage<?> onBinary(@NotNull WebSocket socket, @NotNull ByteBuffer data, boolean last) {
            Connection connection = connections.get(socket);
            if (connection == null || connection.closed) {
                return null;
            }
            activity(socket);
            if (data.remaining() > maxBinaryMessageSize - connection.binary.position()) {
                throw new IllegalStateException("WebSocket binary message exceeds the configured limit");
            }
            int size = connection.binary.position() + data.remaining();
            if (connection.binary.position() == 0 && last) {
                if (active == connection && !connection.closed) {
                    binary(data);
                }
            } else {
                if (connection.binary.capacity() < size) {
                    int capacity = Math.max(1024, connection.binary.capacity());
                    while (capacity < size) {
                        capacity = capacity > maxBinaryMessageSize / 2 ? size : capacity * 2;
                    }
                    ByteBuffer grown = ByteBuffer.allocate(capacity);
                    connection.binary.flip();
                    grown.put(connection.binary);
                    connection.binary = grown;
                }
                connection.binary.put(data);
                if (last) {
                    connection.binary.flip();
                    ByteBuffer message = connection.binary;
                    connection.binary = ByteBuffer.allocate(0);
                    if (active == connection && !connection.closed) {
                        binary(message);
                    }
                }
            }
            if (!connection.closed) {
                socket.request(1);
            }
            return null;
        }

        @Override
        @Nullable
        public CompletionStage<?> onPing(@NotNull WebSocket socket, @NotNull ByteBuffer message) {
            Connection connection = connections.get(socket);
            if (connection != null && !connection.closed) {
                activity(socket);
                socket.request(1);
            }
            return null;
        }

        @Override
        @Nullable
        public CompletionStage<?> onPong(@NotNull WebSocket socket, @NotNull ByteBuffer message) {
            Connection connection = connections.get(socket);
            if (connection != null && !connection.closed) {
                activity(socket);
                socket.request(1);
            }
            return null;
        }

        @Override
        @Nullable
        public CompletionStage<?> onClose(@NotNull WebSocket socket, int statusCode, @NotNull String reason) {
            Connection connection = connections.get(socket);
            finish(connection, statusCode, reason, null, true);
            return null;
        }

        @Override
        public void onError(@NotNull WebSocket socket, @NotNull Throwable error) {
            finish(connections.get(socket), 0, "", error, true);
        }

        public boolean isOpen() {
            Connection connection = active;
            return connection != null && !connection.closed;
        }

        private boolean isOpen(@NotNull WebSocket socket) {
            Connection connection = connections.get(socket);
            return connection != null && !connection.closed;
        }

        public void close(@NotNull WebSocket socket) {
            Connection connection = connections.get(socket);
            if (connection == null) {
                return;
            }
            synchronized (connection) {
                if (connection.closed) {
                    return;
                }
                connection.closed = true;
            }
            startClose(connection);
        }

        private void startClose(@NotNull Connection connection) {
            WebSocket socket = connection.socket;
            CompletableFuture.delayedExecutor(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
                if (connections.containsKey(socket)) {
                    abort(socket);
                }
            });
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "").whenComplete((ignored, error) -> {
                    if (error != null) {
                        abort(socket);
                    } else {
                        finish(connection, WebSocket.NORMAL_CLOSURE, "", null, false);
                    }
                });
            } catch (RuntimeException e) {
                abort(socket);
            }
        }

        private void abort(@NotNull WebSocket socket) {
            Connection connection = connections.get(socket);
            try {
                socket.abort();
            } finally {
                finish(connection, WebSocket.NORMAL_CLOSURE, "", null, true);
            }
        }

        private void finish(
            @Nullable Connection connection,
            int code,
            @NotNull String reason,
            @Nullable Throwable error,
            boolean terminated
        ) {
            if (connection == null) {
                return;
            }
            boolean notify;
            synchronized (connection) {
                connection.closed = true;
                notify = !connection.notified;
                connection.notified = true;
            }
            if (terminated) {
                connections.remove(connection.socket);
                if (connection.owner != null) {
                    connection.owner.unregister(connection.socket);
                }
            }
            if (notify && active == connection) {
                if (error != null) {
                    error(error);
                } else {
                    closed(code, reason);
                }
            }
        }

        protected void text(@NotNull String message) {
        }

        protected void binary(@NotNull ByteBuffer message) {
        }

        protected void closed(int statusCode, @NotNull String reason) {
        }

        protected void error(@NotNull Throwable cause) {
        }
    }
}
