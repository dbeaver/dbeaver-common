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
import com.google.gson.JsonSyntaxException;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonRpcClient extends RpcClient {

    private static final String DEFAULT_USER_AGENT = "JsonRpc Client";

    @NotNull
    public static <T> Builder<T> builder(@NotNull URI uri, @NotNull Class<T> cls) {
        return new Builder<>(uri, cls);
    }

    public static final class Builder<T> {
        private final URI uri;
        private final Class<T> cls;
        private Gson gson;
        private String userAgent;

        private Builder(@NotNull URI uri, @NotNull Class<T> cls) {
            this.uri = uri;
            this.cls = cls;
            this.gson = RpcConstants.COMPACT_GSON;
            this.userAgent = DEFAULT_USER_AGENT;
        }

        @NotNull
        public Builder<T> setGson(@NotNull Gson gson) {
            this.gson = gson;
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
                new JsonRpcInvocationHandler(cls, uri, gson, userAgent));
        }
    }

    private static class JsonRpcInvocationHandler extends HttpTransportInvocationHandler {

        private JsonRpcInvocationHandler(
            @NotNull Class<?> clientClass,
            @NotNull URI uri,
            @NotNull Gson gson,
            @NotNull String userAgent
        ) {
            super(clientClass, uri, gson, userAgent);
        }

        @Override
        protected void handleHttpError(String contents) throws RpcException {
            try {
                Map<?, ?> map = gson.fromJson(contents, Map.class);
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
                        throw rpcException;
                    }
                }
            } catch (JsonSyntaxException ignored) {
            }
            super.handleHttpError(contents);
        }

        @Override
        protected String invokeRemoteMethod(
            @NotNull Method method,
            @Nullable RequestMapping mapping,
            @NotNull Map<String, JsonElement> values
        ) {
            try {
                Map<String, Object> fullRequest = new LinkedHashMap<>();
                List<JsonElement> paramList = new ArrayList<>(values.values());
                fullRequest.put(method.getName(), paramList);
                String requestString = gson.toJson(fullRequest);

                return super.invokeRemoteMethodOverHttp(uri, requestString, mapping);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RpcException(e);
            }
        }

    }

}
