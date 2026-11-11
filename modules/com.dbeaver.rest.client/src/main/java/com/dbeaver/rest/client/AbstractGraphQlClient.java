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
package com.dbeaver.rest.client;

import com.dbeaver.rest.client.interceptor.HttpInterceptor;
import com.google.gson.JsonParseException;
import com.google.gson.ToNumberPolicy;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.utils.GsonUtils;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

/** HTTP transport and response handling shared by GraphQL clients. */
public abstract class AbstractGraphQlClient extends AbstractRestClient {
    protected AbstractGraphQlClient(
        @NotNull String url,
        int connectTimeoutMs,
        int readTimeoutMs,
        @NotNull List<HttpInterceptor> interceptors,
        @NotNull HttpClient.Redirect redirectPolicy
    ) {
        super(url, connectTimeoutMs, readTimeoutMs, interceptors, redirectPolicy);
        gson = GsonUtils.gsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.BIG_DECIMAL).create();
    }

    @NotNull
    protected <T> T executeGraphQl(
        @NotNull String query, @NotNull Class<T> dataType, @NotNull String errorMessage
    ) throws DBException {
        return executeGraphQl(query, Map.of(), dataType, errorMessage);
    }

    @NotNull
    protected <T> T executeGraphQl(
        @NotNull String query, @NotNull Map<String, ?> variables, @NotNull Class<T> dataType, @NotNull String errorMessage
    ) throws DBException {
        Map<String, ?> body = variables.isEmpty() ? Map.of("query", query) : Map.of("query", query, "variables", variables);
        Map<?, ?> response = executePostRequest("graphql", Map.of(), body, Map.class);
        List<?> errors = response != null && response.get("errors") instanceof List<?> list ? list : null;
        if (response == null || errors != null && !errors.isEmpty() || !(response.get("data") instanceof Map<?, ?> data)) {
            throw mapGraphQlError(errorMessage, errors);
        }
        if (dataType.isInstance(data)) {
            return dataType.cast(data);
        }
        try {
            return gson.fromJson(gson.toJsonTree(data), dataType);
        } catch (JsonParseException e) {
            throw new DBException("Invalid GraphQL response", e);
        }
    }

    @NotNull
    protected DBException mapGraphQlError(@NotNull String message, @Nullable List<?> errors) {
        if (errors != null && !errors.isEmpty() && errors.get(0) instanceof Map<?, ?> first
            && first.get("message") instanceof String detail) {
            return new DBException(message + ": " + detail);
        }
        return new DBException(message);
    }
}
