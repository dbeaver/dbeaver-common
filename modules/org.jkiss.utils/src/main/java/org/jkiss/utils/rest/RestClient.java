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
package org.jkiss.utils.rest;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.utils.CommonUtils;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;
import javax.net.ssl.SSLContext;

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
        private SSLContext sslContext;
        private Map<String, String> headers = Map.of();

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

        public Builder<T> setSslContext(@Nullable SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder<T> setHeaders(@NotNull Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        @NotNull
        public T create() {
            return createProxy(
                cls,
                new RestInvocationHandler(cls, uri, gson, resolver, userAgent, sslContext, headers)
            );
        }
    }

    static class RestInvocationHandler extends HttpTransportInvocationHandler {

        private final RestEndpointResolver resolver;

        RestInvocationHandler(
            @NotNull Class<?> clientClass,
            @NotNull URI uri,
            @NotNull Gson gson,
            @NotNull RestEndpointResolver resolver,
            @NotNull String userAgent,
            @Nullable SSLContext sslContext,
            @NotNull Map<String, String> headers
        ) {
            super(clientClass, uri, gson, userAgent, sslContext, headers);
            this.resolver = resolver;
        }

        @Override
        protected String invokeRemoteMethod(
            @NotNull Method method,
            @Nullable RequestMapping mapping,
            @NotNull Map<String, JsonElement> values
        ) {
            try {
                String endpoint = mapping == null ? null : mapping.value();
                if (CommonUtils.isEmpty(endpoint)) {
                    endpoint = resolver.generateEndpointName(method.getName());
                }
                StringBuilder url = new StringBuilder();
                url.append(uri);
                if (url.charAt(url.length() - 1) != '/') url.append('/');
                url.append(endpoint);

                String requestString = gson.toJson(values);

                return super.invokeRemoteMethodOverHttp(URI.create(url.toString()), requestString, mapping);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RpcException(e);
            }
        }

    }
}
