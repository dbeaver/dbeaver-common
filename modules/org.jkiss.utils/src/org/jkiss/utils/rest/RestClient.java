/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
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
package org.jkiss.utils.rest;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.utils.CommonUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RestClient extends RpcClient {

    private static final String DEFAULT_USER_AGENT = "DBeaver RPC Client";

    @NotNull
    public static <T> Builder<T> builder(@NotNull URI uri, @NotNull Class<T> cls) {
        return new Builder<>(uri, cls);
    }

    public static final class Builder<T> {
        private final URI uri;
        private final Class<T> cls;
        private Gson gson;
        private RestEndpointResolver resolver;
        private String userAgent;

        private Builder(@NotNull URI uri, @NotNull Class<T> cls) {
            this.uri = uri;
            this.cls = cls;
            this.gson = RpcConstants.DEFAULT_GSON;
            this.resolver = methodName -> methodName;
            this.userAgent = DEFAULT_USER_AGENT;
        }

        @NotNull
        public Builder<T> setGson(@NotNull Gson gson) {
            this.gson = gson;
            return this;
        }

        public Builder<T> setEndpointResolver(@NotNull RestEndpointResolver resolver) {
            this.resolver = resolver;
            return this;
        }

        public Builder<T> setUserAgent(@NotNull String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        @NotNull
        public T create() {
            return createProxy(
                cls,
                new RestInvocationHandler(cls, uri, gson, resolver, userAgent));
        }
    }

    private static class RestInvocationHandler extends RpcInvocationHandler {

        private final RestEndpointResolver resolver;
        private final ExecutorService httpExecutor;
        private final HttpClient client;

        private RestInvocationHandler(
            @NotNull Class<?> clientClass,
            @NotNull URI uri,
            @NotNull Gson gson,
            @NotNull RestEndpointResolver resolver,
            @NotNull String userAgent
        ) {
            super(clientClass, uri, gson, userAgent);
            this.resolver = resolver;
            this.httpExecutor = Executors.newSingleThreadExecutor();
            this.client = HttpClient.newBuilder()
                .executor(httpExecutor)
                .cookieHandler(new CookieManager())
                .build();
        }

        @Override
        protected String invokeRemoteMethod(@NotNull Method method, @Nullable RequestMapping mapping, @NotNull Parameter[] parameters, @NotNull Map<String, JsonElement> values) {
            try {
                String endpoint = mapping == null ? null : mapping.value();
                if (CommonUtils.isEmpty(endpoint)) {
                    endpoint = resolver.generateEndpointName(method.getName());
                }
                StringBuilder url = new StringBuilder();
                url.append(uri);
                if (url.charAt(url.length() - 1) != '/') url.append('/');
                url.append(endpoint);
                HttpResponse.BodyHandler<String> readerBodyHandler =
                    info -> BodySubscribers.ofString(StandardCharsets.UTF_8);
                String requestString = gson.toJson(values);

                final HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", userAgent)
                    .POST(BodyPublishers.ofString(requestString));

                if (mapping != null && mapping.timeout() > 0) {
                    builder.timeout(Duration.ofSeconds(mapping.timeout()));
                }

                final HttpResponse<String> response = client.send(
                    builder.build(),
                    readerBodyHandler
                );

                String contents = response.body();
                if (response.statusCode() != RpcConstants.SC_OK) {
                    handleRpcError(contents);
                }

                return contents;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RpcException(e);
            }
        }

        @Override
        protected boolean isClientClosed() {
            return httpExecutor == null || httpExecutor.isShutdown() || httpExecutor.isTerminated();
        }

        protected void closeClient() {
            if (!httpExecutor.isShutdown()) {
                httpExecutor.shutdown();
            }
        }

    }

}
