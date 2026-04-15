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
import com.google.gson.JsonNull;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.HttpConstants;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RestServer {
    private static final Logger log = Logger.getLogger(RestServer.class.getName());
    private static final RequestHandlerFactory DEFAULT_HANDLER_FACTORY = new RequestHandlerFactory() {
        @NotNull
        @Override
        public <T> RequestHandler<T> createHandler(
            @NotNull Class<T> cls,
            @NotNull T object,
            @NotNull Gson gson,
            @NotNull Predicate<InetSocketAddress> filter,
            @Nullable String landingPage
        ) {
            return new RequestHandler<>(cls, object, gson, filter, landingPage);
        }
    };

    private HttpServer server;
    private String landingPage;

    public <T> RestServer(
        @NotNull Class<T> cls,
        @NotNull T object,
        @NotNull Gson gson,
        @NotNull Predicate<InetSocketAddress> filter,
        int port,
        int backlog
    ) throws IOException {
        this(cls, object, gson, filter, port, backlog, DEFAULT_HANDLER_FACTORY);
    }

    public <T> RestServer(
        @NotNull Class<T> cls,
        @NotNull T object,
        @NotNull Gson gson,
        @NotNull Predicate<InetSocketAddress> filter,
        int port,
        int backlog,
        @NotNull RequestHandlerFactory handlerFactory
    ) throws IOException {
        this(
            Collections.singletonList(new ControllerDef<>("/", cls, object)),
            gson,
            filter,
            port,
            backlog,
            null,
            handlerFactory
        );
    }

    private RestServer(
        @NotNull List<ControllerDef<?>> controllers,
        @NotNull Gson gson,
        @NotNull Predicate<InetSocketAddress> filter,
        int port,
        int backlog,
        @Nullable String landingPage,
        @NotNull RequestHandlerFactory handlerFactory
    ) throws IOException {
        this.landingPage = landingPage;
        InetSocketAddress listenAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
        server = HttpServer.create(listenAddr, backlog);
        controllers.forEach(ctrl ->
            server.createContext(ctrl.path, createHandler(ctrl, gson, filter, landingPage, handlerFactory))
        );
        server.setExecutor(createExecutor());
        server.start();
    }

    @NotNull
    public static <T> Builder builder(@NotNull Class<T> cls, @NotNull T object) {
        Builder builder = new Builder();
        builder.addController("/", cls, object);
        return builder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isRunning() {
        return server != null;
    }

    public void stop() {
        stop(1);
    }

    public void stop(int delay) {
        try {
            server.stop(delay);

            final Executor executor = server.getExecutor();
            if (executor instanceof ExecutorService) {
                ((ExecutorService) executor).shutdown();
            }
        } finally {
            server = null;
        }
    }

    @NotNull
    public InetSocketAddress getAddress() {
        return server.getAddress();
    }

    void setLandingPage(@Nullable String landingPage) {
        this.landingPage = landingPage;
    }

    @NotNull
    protected Executor createExecutor() {
        return new ThreadPoolExecutor(1, 10, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    @NotNull
    private static <T> RequestHandler<T> createHandler(
        @NotNull ControllerDef<T> controller,
        @NotNull Gson gson,
        @NotNull Predicate<InetSocketAddress> filter,
        @Nullable String landingPage,
        @NotNull RequestHandlerFactory handlerFactory
    ) {
        return handlerFactory.createHandler(controller.cls, controller.instance, gson, filter, landingPage);
    }

    private static final Type REQUEST_TYPE = new TypeToken<Map<String, JsonElement>>() {}.getType();

    public static class RequestHandler<T> implements HttpHandler {

        private final T object;
        private final Gson gson;
        private final Map<String, Method> mappings;
        private final Predicate<InetSocketAddress> filter;
        @Nullable
        private final String landingPage;

        public RequestHandler(
            @NotNull Class<T> cls,
            @NotNull T object,
            @NotNull Gson gson,
            @NotNull Predicate<InetSocketAddress> filter,
            @Nullable String landingPage
        ) {
            this.object = object;
            this.gson = gson;
            this.mappings = createMappings(cls);
            this.filter = filter;
            this.landingPage = landingPage;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Response<?> response;
                try {
                    response = executeRequest(exchange);
                } catch (IOException e) {
                    log.log(Level.SEVERE, "IO error", e);
                    response = new Response<>(e.getMessage(), String.class, 500);
                }

                Object responseObject = response.object;
                if (responseObject == null) {
                    responseObject = "Internal error";
                }
                if (response.code == RpcConstants.SC_OK) {
                    String responseText;
                    if (response.type == void.class) {
                        responseText = CommonUtils.toString(response.object);
                        exchange.getResponseHeaders().add(HttpConstants.HEADER_CONTENT_TYPE, HttpConstants.CONTENT_TYPE_TEXT_PLAIN);
                    } else {
                        try {
                            responseText = gson.toJson(response.object, response.type);
                        } catch (Throwable e) {
                            StringWriter buf = new StringWriter();
                            new RpcException("JSON serialization error: " + e.getMessage(), e).printStackTrace(new PrintWriter(buf, true));

                            sendError(exchange, RpcConstants.SC_SERVER_ERROR, buf.toString());
                            return;
                        }
                        exchange.getResponseHeaders().add(HttpConstants.HEADER_CONTENT_TYPE, HttpConstants.CONTENT_TYPE_JSON);
                    }
                    byte[] responseBytes = responseText.getBytes(StandardCharsets.UTF_8);

                    exchange.sendResponseHeaders(RpcConstants.SC_OK, responseBytes.length);
                    try (OutputStream responseBody = exchange.getResponseBody()) {
                        responseBody.write(responseBytes);
                    }
                } else {
                    sendError(exchange, response.code, responseObject);
                }
            } catch (Throwable e) {
                log.log(Level.SEVERE, "Internal IO error", e);
                throw e;
            } finally {
                exchange.close();
            }
        }

        protected void sendError(HttpExchange exchange, int resultCode, Object responseObject) throws IOException {
            String responseText = responseObject.toString();
            byte[] result = responseText.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().add(HttpConstants.HEADER_CONTENT_TYPE, HttpConstants.CONTENT_TYPE_TEXT_PLAIN);
            exchange.sendResponseHeaders(resultCode, result.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(result);
            }
        }

        @NotNull
        private Response<?> executeRequest(@NotNull HttpExchange exchange) throws IOException {
            if (!filter.test(exchange.getRemoteAddress())) {
                return new Response<>("Access is forbidden", String.class, RpcConstants.SC_FORBIDDEN);
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                if (landingPage != null && exchange.getRequestURI().getPath().equals("/")) {
                    return new Response<>(landingPage, void.class, RpcConstants.SC_OK);
                }
                return new Response<>("Unsupported method", String.class, RpcConstants.SC_UNSUPPORTED);
            }

            final URI uri = exchange.getRequestURI();
            final String context = exchange.getHttpContext().getPath();
            String path = uri.getPath();
            if (path.startsWith(context)) {
                path = path.substring(context.length());
            }
            path = path.replaceAll("^/+", "");
            final Method method = mappings.get(path);

            if (method == null) {
                return new Response<>("Mapping " + path + " not found", String.class, RpcConstants.SC_NOT_FOUND);
            }

            final Map<String, JsonElement> request;

            try (Reader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))) {
                request = gson.fromJson(reader, REQUEST_TYPE);
            }

            final Parameter[] parameters = method.getParameters();
            final Object[] values = new Object[parameters.length];

            for (int i = 0; i < parameters.length; i++) {
                final Parameter p = parameters[i];
                final RequestParameter param = p.getDeclaredAnnotation(RequestParameter.class);
                final JsonElement element = request.getOrDefault(param.value(), JsonNull.INSTANCE);
                values[i] = gson.fromJson(element, p.getParameterizedType());
            }

            try {
                final Object result = method.invoke(object, values);
                final Type type = method.getGenericReturnType();
                return createResponseContent(result, type);
            } catch (Throwable e) {
                if (e instanceof InvocationTargetException) {
                    InvocationTargetException ite = (InvocationTargetException) e;
                    e = ite.getTargetException();
                }
                log.log(Level.SEVERE, "RPC call '" + uri + "' failed: " + e.getMessage());
                return createResponseError(e);
            }
        }

        @NotNull
        protected Map<String, Method> createMappings(@NotNull Class<T> cls) {
            final Map<String, Method> mappings = new HashMap<>();

            for (Method method : cls.getMethods()) {
                if (method.getDeclaringClass() == Object.class) {
                    continue;
                }

                final RequestMapping mapping = method.getDeclaredAnnotation(RequestMapping.class);

                if (mapping == null) {
                    continue;
                }

                String methodEndpoint = mapping.value();
                if (CommonUtils.isEmptyTrimmed(mapping.value())) {
                    methodEndpoint = method.getName();
                }

                if (mappings.containsKey(methodEndpoint)) {
                    log.warning("Method " + method + " has duplicate mapping, skipping");
                    continue;
                }

                method.setAccessible(true);
                mappings.put(methodEndpoint, method);
            }

            return Collections.unmodifiableMap(mappings);
        }
    }

    @NotNull
    private static Response<String> createResponseError(Throwable e) {
        StringWriter buf = new StringWriter();
        e.printStackTrace(new PrintWriter(buf, true));
        return new Response<>(buf.toString(), String.class, RpcConstants.SC_SERVER_ERROR);
    }

    @NotNull
    private static Response<Object> createResponseContent(Object result, Type type) {
        return new Response<>(result, type, RpcConstants.SC_OK);
    }

    public static final class Builder {
        private static final Predicate<InetSocketAddress> DEFAULT_PREDICATE = address -> true;

        private Gson gson;
        private int port;
        private int backlog;
        private Predicate<InetSocketAddress> filter = DEFAULT_PREDICATE;
        private String landingPage;
        private final List<ControllerDef<?>> controllers = new ArrayList<>();
        private RequestHandlerFactory handlerFactory = DEFAULT_HANDLER_FACTORY;

        private Builder() {
            this.gson = RpcConstants.DEFAULT_GSON;
            this.port = 0;
            this.backlog = 0;
        }

        @NotNull
        public <T> Builder addController(
            @NotNull String path,
            @NotNull Class<T> cls,
            @NotNull T instance
        ) {
            String normalizedPath = path.startsWith("/") ? path : "/" + path;
            controllers.add(new ControllerDef<>(normalizedPath, cls, instance));
            return this;
        }

        @NotNull
        public Builder setGson(@NotNull Gson gson) {
            this.gson = gson;
            return this;
        }

        @NotNull
        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        @NotNull
        public Builder setBacklog(int backlog) {
            this.backlog = backlog;
            return this;
        }

        @NotNull
        public Builder setLandingPage(String landingPage) {
            this.landingPage = landingPage;
            return this;
        }

        @NotNull
        public Builder setFilter(@NotNull Predicate<InetSocketAddress> filter) {
            this.filter = filter;
            return this;
        }

        public Builder setHandlerFactory(@NotNull RequestHandlerFactory handlerFactory) {
            this.handlerFactory = handlerFactory;
            return this;
        }

        @NotNull
        public RestServer create() {
            try {
                return new RestServer(controllers, gson, filter, port, backlog, landingPage, handlerFactory);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

    private static class Response<T> {
        private final T object;
        private final Type type;
        private final int code;

        public Response(@Nullable T object, @NotNull Type type, int code) {
            this.object = object;
            this.type = type;
            this.code = code;
        }
    }

    private static final class ControllerDef<T> {
        final String path;
        final Class<T> cls;
        final T instance;

        ControllerDef(String path, Class<T> cls, T instance) {
            this.path = path;
            this.cls = cls;
            this.instance = instance;
        }
    }


}
