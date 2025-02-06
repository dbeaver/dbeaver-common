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
package com.dbeaver.rpc.ws;

import com.dbeaver.rpc.api.RpcRequest;
import com.dbeaver.rpc.api.RpcResponse;
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

/**
 * A WebSocket client for sending and receiving messages with correlation IDs.
 */
public final class WsClient implements MessageHandler.Whole<String> {
    private static final Gson GSON = RpcConstants.COMPACT_GSON;

    private final Session session;
    private final Map<UUID, CompletableFuture<String>> pendingMessages = new ConcurrentHashMap<>();

    /**
     * Creates a new client using an existing WebSocket Session.
     */
    public WsClient(Session session) {
        this.session = session;
        // Register this instance as the message handler.
        this.session.addMessageHandler(this);
    }

    /**
     * Sends a message synchronously and blocks until a response is received.
     *
     * @param payload The data to send.
     * @return The response message.
     * @throws IOException if the message fails to send.
     */
    public String sendMessage(String payload) throws IOException {
        return sendMessageAsync(payload).join();
    }

    /**
     * Sends a message asynchronously.
     *
     * @param payload The data to send.
     * @return A CompletableFuture that will complete when a response arrives.
     * @throws IOException if the message fails to send.
     */
    public CompletableFuture<String> sendMessageAsync(String payload) throws IOException {
        UUID messageId = UUID.randomUUID();

        // Send the text message over the WebSocket.
        RpcRequest methodInvocation = new RpcRequest(messageId, payload);
        String message = GSON.toJson(methodInvocation);
        session.getBasicRemote().sendText(message);

        // Store a future so we can complete it when the response arrives.
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingMessages.put(messageId, future);

        return future;
    }

    @Override
    public void onMessage(String rawMessage) {
        RpcResponse result = GSON.fromJson(rawMessage, RpcResponse.class);

        CompletableFuture<String> future = pendingMessages.remove(result.messageId());
        if (future == null) {
            // No future found for this message ID.
            return;
        }

        if (result.error() != null) {
            future.completeExceptionally(parseError(result.error()));
        } else {
            future.complete(result.result());
        }
    }

    /**
     * Closes the WebSocket session.
     */
    public void close() {
        try {
            session.close();
        } catch (IOException e) {
            // Handle close error as needed.
        }
    }

    private static Exception parseError(String contents) {
        try {
            Map<?, ?> map = GSON.fromJson(contents, Map.class);
            Map<String, Object> error = (Map<String, Object>) map.get("error");
            if (error != null) {
                Object errorClass = error.get("exceptionClass");
                Object message = error.get("message");
                Object stacktrace = error.get("stacktrace");
                if (message != null) {
                    RpcException rpcException = new RpcException(
                        message.toString(),
                        errorClass == null ? null : errorClass.toString());
                    //rpcException.setStackTrace();
                    return rpcException;
                }
            }
        } catch (JsonSyntaxException ignored) {
            return new IOException(contents);
        }

        return new IOException(contents);
    }
}
