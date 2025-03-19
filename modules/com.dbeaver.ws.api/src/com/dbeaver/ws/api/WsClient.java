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

import org.jkiss.utils.rpc.RpcRequest;
import org.jkiss.utils.rpc.RpcResponse;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import org.jkiss.utils.rest.RpcConstants;
import org.jkiss.utils.rest.RpcException;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;


/**
 * A WebSocket client for sending and receiving messages with correlation IDs.
 */
public final class WsClient implements MessageHandler.Whole<String> {
    private static final Logger logger = Logger.getLogger(WsClient.class.getName());
    private static final Gson GSON = RpcConstants.COMPACT_GSON;

    private final Session session;
    private final Map<UUID, CompletableFuture<String>> pendingMessages = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean closed = false;

    public WsClient(Session session) {
        this.session = session;
        session.addMessageHandler(this);
    }

    /**
     * Sends a message and waits for a response.
     *
     * @param payload the message payload
     * @return the response payload
     * @throws IOException if the connection is closed
     */
    public String sendMessage(String payload) throws IOException {
        return sendMessageAsync(payload).join();
    }

    private CompletableFuture<String> sendMessageAsync(String payload) throws IOException {
        if (closed) {
            throw new IOException("Connection closed");
        }
        UUID messageId = UUID.randomUUID();
        sendRpcRequest(messageId, payload);
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingMessages.put(messageId, future);
        return future;
    }

    private void sendRpcRequest(UUID messageId, String payload) throws IOException {
        lock.readLock().lock();
        try {
            RpcRequest request = new RpcRequest(messageId, payload);
            String message = GSON.toJson(request);
            session.getBasicRemote().sendText(message);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void onMessage(String rawMessage) {
        RpcResponse response = GSON.fromJson(rawMessage, RpcResponse.class);
        CompletableFuture<String> future = pendingMessages.remove(response.messageId());
        if (future == null) {
            return;
        }
        if (response.error() != null) {
            future.completeExceptionally(parseError(response.error()));
        } else {
            future.complete(response.result());
        }
    }

    /**
     * Closes the connection. This method is safe to call multiple times.
     */
    public void close() {
        // Guard against multiple closures.
        if (closed) {
            return;
        }

        lock.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            session.close();
        } catch (IOException e) {
            logger.warning("Error closing session: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }

        // Fail any pending messages.
        pendingMessages.forEach((id, future) ->
            future.completeExceptionally(new IOException("Connection closed"))
        );
        pendingMessages.clear();
    }

    private static Exception parseError(String contents) {
        try {
            Map<?, ?> map = GSON.fromJson(contents, Map.class);
            Map<String, Object> error = (Map<String, Object>) map.get("error");
            if (error != null) {
                Object errorClass = error.get("exceptionClass");
                Object message = error.get("message");
                if (message != null) {
                    return new RpcException(
                        message.toString(),
                        errorClass != null ? errorClass.toString() : null
                    );
                }
            }
        } catch (JsonSyntaxException ignored) {
            return new IOException(contents);
        }
        return new IOException(contents);
    }
}
