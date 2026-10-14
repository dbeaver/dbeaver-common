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
package org.jkiss.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
}
