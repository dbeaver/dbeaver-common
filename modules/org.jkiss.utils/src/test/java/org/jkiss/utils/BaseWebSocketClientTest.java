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

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BaseWebSocketClientTest {
    @Test
    void assemblesFragmentsAndRequestsFurtherMessages() {
        AtomicInteger requests = new AtomicInteger();
        WebSocket socket = (WebSocket) Proxy.newProxyInstance(
            WebSocket.class.getClassLoader(),
            new Class<?>[] {WebSocket.class},
            (proxy, method, args) -> {
                if (method.getName().equals("request")) {
                    requests.incrementAndGet();
                }
                return null;
            }
        );
        List<String> texts = new ArrayList<>();
        List<byte[]> binaries = new ArrayList<>();
        var listener = new BaseWebSocketClient.Listener() {
            @Override
            protected void text(String message) {
                texts.add(message);
            }

            @Override
            protected void binary(ByteBuffer message) {
                byte[] bytes = new byte[message.remaining()];
                message.get(bytes);
                binaries.add(bytes);
            }
        };

        listener.onOpen(socket);
        listener.onText(socket, "hel", false);
        listener.onText(socket, "lo", true);
        listener.onBinary(socket, ByteBuffer.wrap(new byte[] {1, 2, 3}), false);
        listener.onBinary(socket, ByteBuffer.wrap(new byte[] {4, 5}), true);
        listener.onPing(socket, ByteBuffer.wrap(new byte[] {9}));
        assertEquals(List.of("hello"), texts);
        assertEquals(1, binaries.size());
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, binaries.get(0));
        assertEquals(6, requests.get());
        listener.onClose(socket, 1000, "done");
        assertFalse(listener.isOpen());
    }

    @Test
    void rejectsOversizedFragmentedBinaryMessage() {
        WebSocket socket = (WebSocket) Proxy.newProxyInstance(
            WebSocket.class.getClassLoader(),
            new Class<?>[] {WebSocket.class},
            (proxy, method, args) -> null
        );
        var listener = new BaseWebSocketClient.Listener() {};
        listener.setMaxBinaryMessageSize(4);
        listener.onOpen(socket);
        listener.onBinary(socket, ByteBuffer.wrap(new byte[] {1, 2, 3}), false);
        assertThrows(IllegalStateException.class, () ->
            listener.onBinary(socket, ByteBuffer.wrap(new byte[] {4, 5}), true)
        );
    }

    @Test
    void reconnectDropsPartialMessageAndOldClose() {
        WebSocket socket = (WebSocket) Proxy.newProxyInstance(
            WebSocket.class.getClassLoader(),
            new Class<?>[] {WebSocket.class},
            (proxy, method, args) -> null
        );
        WebSocket replacement = (WebSocket) Proxy.newProxyInstance(
            WebSocket.class.getClassLoader(),
            new Class<?>[] {WebSocket.class},
            (proxy, method, args) -> null
        );
        List<String> messages = new ArrayList<>();
        var listener = new BaseWebSocketClient.Listener() {
            @Override
            protected void text(String message) {
                messages.add(message);
            }
        };
        listener.onOpen(socket);
        listener.onText(socket, "old", false);
        listener.onOpen(replacement);
        listener.onClose(socket, 1000, "done");
        listener.onText(replacement, "new", true);
        assertEquals(List.of("new"), messages);
        assertTrue(listener.isOpen());
    }

    @Test
    void reconnectKeepsBinaryFragmentsSeparate() {
        WebSocket old = socket();
        WebSocket replacement = socket();
        List<byte[]> messages = new ArrayList<>();
        var listener = new BaseWebSocketClient.Listener() {
            @Override
            protected void binary(ByteBuffer data) {
                byte[] message = new byte[data.remaining()];
                data.get(message);
                messages.add(message);
            }
        };
        listener.onOpen(old);
        listener.onBinary(old, ByteBuffer.wrap(new byte[] {1}), false);
        listener.onOpen(replacement);
        listener.onBinary(replacement, ByteBuffer.wrap(new byte[] {2}), false);
        listener.onBinary(old, ByteBuffer.wrap(new byte[] {3}), true);
        listener.onBinary(replacement, ByteBuffer.wrap(new byte[] {4}), true);
        assertEquals(1, messages.size());
        assertArrayEquals(new byte[] {2, 4}, messages.get(0));
    }

    @Test
    void startsClosedAndDisablesNonPositiveIdleTimeouts() throws InterruptedException {
        var listener = new BaseWebSocketClient.Listener() {};
        assertFalse(listener.isOpen());
        listener.setIdleTimeout(Duration.ZERO);
        listener.onOpen(socket());
        Thread.sleep(50);
        assertTrue(listener.isOpen());
    }

    @Test
    void directConnectionsIgnoreDefaultProxy() throws Exception {
        try (var client = new BaseWebSocketClient(false)) {
            Field field = BaseWebSocketClient.class.getDeclaredField("client");
            field.setAccessible(true);
            HttpClient httpClient = (HttpClient) field.get(client);
            ProxySelector selector = httpClient.proxy().orElseThrow();
            assertEquals(java.net.Proxy.NO_PROXY, selector.select(URI.create("http://localhost")).get(0));
        }
    }

    @Test
    void shutdownNotifiesConnectedListener() throws Exception {
        try (ServerSocket server = new ServerSocket(0); BaseWebSocketClient client = new BaseWebSocketClient(false)) {
            CountDownLatch accepted = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Thread peer = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    sendHandshake(socket);
                    accepted.countDown();
                    release.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    accepted.countDown();
                }
            });
            peer.setDaemon(true);
            peer.start();
            CountDownLatch closed = new CountDownLatch(1);
            var listener = new BaseWebSocketClient.Listener() {
                @Override
                protected void closed(int statusCode, String reason) {
                    closed.countDown();
                }
            };
            try {
                client.connect(URI.create("ws://localhost:" + server.getLocalPort()), Map.of(), listener);
                assertTrue(accepted.await(2, TimeUnit.SECONDS));
                assertTrue(listener.isOpen());
                client.close();
                assertTrue(closed.await(2, TimeUnit.SECONDS));
                assertFalse(listener.isOpen());
            } finally {
                release.countDown();
                peer.join(2000);
            }
        }
    }

    @Test
    void interruptingHandshakeDoesNotLeaveAnOpenConnection() throws Exception {
        try (ServerSocket server = new ServerSocket(0); BaseWebSocketClient client = new BaseWebSocketClient(false)) {
            CountDownLatch accepted = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Thread peer = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    accepted.countDown();
                    release.await(2, TimeUnit.SECONDS);
                    sendHandshake(socket);
                } catch (Exception ignored) {
                    accepted.countDown();
                }
            });
            peer.setDaemon(true);
            peer.start();

            AtomicInteger opens = new AtomicInteger();
            var listener = new BaseWebSocketClient.Listener() {
                @Override
                public void onOpen(WebSocket socket) {
                    opens.incrementAndGet();
                    super.onOpen(socket);
                }
            };
            CountDownLatch interrupted = new CountDownLatch(1);
            AtomicReference<Throwable> unexpected = new AtomicReference<>();
            Thread connector = new Thread(() -> {
                try {
                    client.connect(URI.create("ws://localhost:" + server.getLocalPort()), Map.of(), listener);
                    unexpected.set(new AssertionError("Handshake unexpectedly succeeded"));
                } catch (InterruptedException expected) {
                    interrupted.countDown();
                } catch (Exception e) {
                    unexpected.set(e);
                }
            });
            connector.start();
            try {
                assertTrue(accepted.await(2, TimeUnit.SECONDS));
                connector.interrupt();
                assertTrue(interrupted.await(2, TimeUnit.SECONDS));
                assertNull(unexpected.get());
                assertFalse(listener.isOpen());
            } finally {
                release.countDown();
                connector.join(2000);
                peer.join(2000);
                client.close();
                assertFalse(listener.isOpen());
            }
            assertEquals(0, opens.get());
        }
    }

    private static void sendHandshake(Socket socket) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        String key = null;
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("sec-websocket-key:")) {
                key = line.substring(line.indexOf(':') + 1).trim();
            }
        }
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(
            (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)
        );
        String response = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n" +
            "Connection: Upgrade\r\nSec-WebSocket-Accept: " + Base64.getEncoder().encodeToString(digest) + "\r\n\r\n";
        socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static WebSocket socket() {
        return (WebSocket) Proxy.newProxyInstance(
            WebSocket.class.getClassLoader(),
            new Class<?>[] {WebSocket.class},
            (proxy, method, args) -> null
        );
    }

    @Test
    void closesIdleConnection() throws InterruptedException {
        CountDownLatch closeFrame = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        WebSocket socket = (WebSocket) Proxy.newProxyInstance(
            WebSocket.class.getClassLoader(),
            new Class<?>[] {WebSocket.class},
            (proxy, method, args) -> {
                if (method.getName().equals("sendClose")) {
                    closeFrame.countDown();
                    return CompletableFuture.completedFuture((WebSocket) proxy);
                }
                return null;
            }
        );
        var listener = new BaseWebSocketClient.Listener() {
            @Override
            protected void closed(int statusCode, String reason) {
                closed.countDown();
            }
        };
        listener.setIdleTimeout(Duration.ofMillis(50));
        listener.onOpen(socket);
        assertTrue(closeFrame.await(2, TimeUnit.SECONDS));
        assertTrue(closed.await(2, TimeUnit.SECONDS));
        assertFalse(listener.isOpen());
    }

    @Test
    void outboundActivityExtendsIdleDeadline() throws InterruptedException {
        CountDownLatch closed = new CountDownLatch(1);
        WebSocket socket = (WebSocket) Proxy.newProxyInstance(
            WebSocket.class.getClassLoader(),
            new Class<?>[] {WebSocket.class},
            (proxy, method, args) -> method.getName().equals("sendClose")
                ? CompletableFuture.completedFuture((WebSocket) proxy) : null
        );
        var listener = new BaseWebSocketClient.Listener() {
            @Override
            protected void closed(int statusCode, String reason) {
                closed.countDown();
            }
        };
        listener.setIdleTimeout(Duration.ofMillis(800));
        listener.onOpen(socket);
        Thread.sleep(300);
        listener.activity(socket);
        Thread.sleep(550);
        assertTrue(listener.isOpen());
        assertTrue(closed.await(2, TimeUnit.SECONDS));
        listener.onClose(socket, 1000, "done");
    }

    @Test
    void localCloseWaitsForCloseFrameSend() {
        CompletableFuture<WebSocket> sent = new CompletableFuture<>();
        AtomicInteger callbacks = new AtomicInteger();
        WebSocket socket = (WebSocket) Proxy.newProxyInstance(
            WebSocket.class.getClassLoader(),
            new Class<?>[] {WebSocket.class},
            (proxy, method, args) -> method.getName().equals("sendClose") ? sent : null
        );
        var listener = new BaseWebSocketClient.Listener() {
            @Override
            protected void closed(int statusCode, String reason) {
                callbacks.incrementAndGet();
            }
        };
        listener.onOpen(socket);
        listener.close(socket);
        assertFalse(listener.isOpen());
        assertEquals(0, callbacks.get());
        sent.complete(socket);
        assertEquals(1, callbacks.get());
        listener.onClose(socket, 1000, "done");
        assertEquals(1, callbacks.get());
    }
}
