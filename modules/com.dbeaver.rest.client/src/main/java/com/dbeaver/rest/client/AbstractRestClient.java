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
package com.dbeaver.rest.client;

import com.dbeaver.rest.client.interceptor.HttpInterceptor;
import com.dbeaver.rest.client.interceptor.HttpRequestWrapper;
import com.dbeaver.rest.client.interceptor.HttpResponseWrapper;
import com.dbeaver.rest.client.interceptor.InterceptorChain;
import com.google.gson.Gson;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.GsonUtils;
import org.jkiss.utils.HttpConstants;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public abstract class AbstractRestClient {

    private static final Logger log = Logger.getLogger(AbstractRestClient.class.getName());

    public static final boolean DISABLE_SSL_CERT_VALIDATION = Boolean.getBoolean("dbeaver.ssl.disableCertificateValidation");

    public static final int DEFAULT_CONNECT_TIMEOUT = 5000;   // 5s
    public static final int DEFAULT_READ_TIMEOUT = 120000;    // 2min

    private static final List<RestExceptionHandler> exceptionHandlers = new ArrayList<>();

    private final HttpClient httpClient;
    private final String apiUrl;
    protected Gson gson = GsonUtils.gsonBuilder().create();

    private final List<HttpInterceptor> interceptors;
    private final int readTimeoutMs;

    public static void addExceptionHandler(@NotNull RestExceptionHandler handler) {
        exceptionHandlers.add(handler);
    }

    public static void removeExceptionHandler(@NotNull RestExceptionHandler handler) {
        exceptionHandlers.remove(handler);
    }

    protected AbstractRestClient(@NotNull String apiUrl, @NotNull List<HttpInterceptor> interceptors) {
        this(apiUrl, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, interceptors);
    }

    protected AbstractRestClient(
        @NotNull String apiUrl,
        int connectTimeoutMs,
        int readTimeoutMs,
        @NotNull List<HttpInterceptor> interceptors
    ) {
        this.apiUrl = apiUrl;
        this.readTimeoutMs = readTimeoutMs > 0 ? readTimeoutMs : DEFAULT_READ_TIMEOUT;

        this.httpClient = buildClient(connectTimeoutMs);
        this.interceptors = prepareInterceptors(interceptors);
    }

    @NotNull
    protected List<HttpInterceptor> prepareInterceptors(@NotNull List<HttpInterceptor> interceptors) {
        return interceptors;
    }

    @NotNull
    private HttpClient buildClient(int connectTimeoutMs) {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs > 0 ? connectTimeoutMs : DEFAULT_CONNECT_TIMEOUT))
            .sslContext(HttpClientUtils.createSSLContext())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @NotNull
    protected HttpClient getHttpClient() {
        return httpClient;
    }

    @NotNull
    private static <T> T checkNotNullResult(@NotNull String endpointUrl, @Nullable T result) throws DBException {
        if (result == null) {
            throw new DBException("Endpoint '" + endpointUrl + "' returned null value");
        }
        return result;
    }

    @NotNull
    protected <T> T get(
        @NotNull String endpoint,
        @NotNull Map<String, ?> params,
        @NotNull Type type
    ) throws DBException {
        URI uri = buildUri(endpoint, params);

        return execute(HttpRequest.newBuilder(uri).GET(), type);
    }

    @NotNull
    protected <T> T post(
        @NotNull String endpoint,
        @NotNull Map<String, ?> params,
        @Nullable Object body,
        @NotNull MediaType mediaType,
        @NotNull Type type
    ) throws DBException {

        URI uri = buildUri(endpoint, params);

        HttpRequest.BodyPublisher publisher = createBodyPublisher(body, mediaType);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .header(HttpConstants.HEADER_CONTENT_TYPE, mediaType.toString())
            .POST(publisher);

        return execute(builder, type);
    }

    @NotNull
    protected <T> T put(
        @NotNull String endpoint,
        @NotNull Map<String, ?> params,
        @Nullable Object body,
        @NotNull MediaType mediaType,
        @NotNull Type type
    ) throws DBException {

        URI uri = buildUri(endpoint, params);

        HttpRequest.BodyPublisher publisher = createBodyPublisher(body, mediaType);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .header(HttpConstants.HEADER_CONTENT_TYPE, mediaType.toString())
            .PUT(publisher);

        return execute(builder, type);
    }

    @NotNull
    protected <T> T delete(
        @NotNull String endpoint,
        @NotNull Map<String, ?> params,
        @Nullable Object body,
        @NotNull MediaType mediaType,
        @NotNull Type type
    ) throws DBException {

        URI uri = buildUri(endpoint, params);
        HttpRequest.BodyPublisher publisher = createBodyPublisher(body, mediaType);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .header(HttpConstants.HEADER_CONTENT_TYPE, mediaType.toString())
            .method("DELETE", publisher);

        return execute(builder, type);
    }

    @NotNull
    protected String buildEndpointUrl(@NotNull String... pathParts) {
        return String.join("/", pathParts);
    }

    @Nullable
    protected <T> T executeGetRequest(@NotNull String endpointUrl, @NotNull Type type) throws DBException {
        return executeGetRequest(endpointUrl, Map.of(), type);
    }

    @NotNull
    protected <T> T executeGetRequestVal(@NotNull String endpointUrl, @NotNull Type type) throws DBException {
        return checkNotNullResult(endpointUrl, executeGetRequest(endpointUrl, Map.of(), type));
    }

    @Nullable
    protected <T> T executeGetRequest(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @NotNull Type type
    ) throws DBException {
        return executeGetRequest(endpointUrl, parameters, MediaType.JSON, type);
    }

    @NotNull
    protected <T> T executeGetRequestVal(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @NotNull Type type
    ) throws DBException {
        return checkNotNullResult(endpointUrl, executeGetRequest(endpointUrl, parameters, MediaType.JSON, type));
    }

    @NotNull
    protected <T> T executeGetRequest(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @Nullable MediaType resultMediaType,
        @NotNull Type type
    ) throws DBException {
        return get(endpointUrl, parameters, type);
    }

    @Nullable
    protected <T> T executeDeleteRequest(@NotNull String endpointUrl, @NotNull Type type) throws DBException {
        return executeDeleteRequest(endpointUrl, Map.of(), type);
    }

    @Nullable
    protected <T> T executeDeleteRequest(@NotNull String endpointUrl, @NotNull Map<String, ?> parameters, @NotNull Type type) throws DBException {
        return executeDeleteRequest(endpointUrl, parameters, null, type);
    }

    @NotNull
    protected <T> T executeDeleteRequestVal(@NotNull String endpointUrl, @NotNull Map<String, ?> parameters, @NotNull Type type) throws DBException {
        return checkNotNullResult(endpointUrl, executeDeleteRequest(endpointUrl, parameters, null, type));
    }

    @Nullable
    protected <T> T executeDeleteRequest(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @Nullable Object body,
        @NotNull Type type
    ) throws DBException {
        return delete(endpointUrl, parameters, body, MediaType.JSON, type);
    }

    @Nullable
    protected <T> T executePostRequest(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @NotNull Type type
    ) throws DBException {
        return executePostRequest(endpointUrl, parameters, null, type);
    }

    @Nullable
    protected <T> T executePostRequest(
        @NotNull String endpointUrl,
        @Nullable Object body,
        @NotNull Type type
    ) throws DBException {
        return executePostRequest(endpointUrl, Map.of(), body, type);
    }

    @NotNull
    protected <T> T executePostRequestVal(
        @NotNull String endpointUrl,
        @Nullable Object body,
        @NotNull Type type
    ) throws DBException {
        return checkNotNullResult(endpointUrl, executePostRequest(endpointUrl, Map.of(), body, type));
    }

    @NotNull
    protected <T> T executePostRequestVal(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @NotNull Type type
    ) throws DBException {
        return checkNotNullResult(endpointUrl, executePostRequest(endpointUrl, parameters, null, type));
    }

    @Nullable
    protected <T> T executePostRequest(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @Nullable Object body,
        @NotNull Type type
    ) throws DBException {
        return executePostRequest(endpointUrl, parameters, body, MediaType.JSON, type);
    }

    @NotNull
    protected <T> T executePostRequestVal(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @Nullable Object body,
        @NotNull Type type
    ) throws DBException {
        return checkNotNullResult(endpointUrl, executePostRequest(endpointUrl, parameters, body, MediaType.JSON, type));
    }

    @Nullable
    protected <T> T executePostRequest(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @Nullable Object body,
        @NotNull MediaType mediaType,
        @NotNull Type type
    ) throws DBException {
        return post(endpointUrl, parameters, body, mediaType, type);
    }

    @NotNull
    protected <T> T executePostRequestVal(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @Nullable Object body,
        @NotNull MediaType mediaType,
        @NotNull Type type
    ) throws DBException {
        return checkNotNullResult(endpointUrl, post(endpointUrl, parameters, body, mediaType, type));
    }

    @Nullable
    protected <T> T executePutRequest(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @NotNull Type type
    ) throws DBException {
        return executePutRequest(endpointUrl, parameters, null, type);
    }

    @NotNull
    protected <T> T executePutRequestVal(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @NotNull Type type
    ) throws DBException {
        return checkNotNullResult(endpointUrl, executePutRequest(endpointUrl, parameters, null, type));
    }

    @Nullable
    protected <T> T executePutRequest(
        @NotNull String endpointUrl,
        @Nullable Object body,
        @NotNull Type type
    ) throws DBException {
        return executePutRequest(endpointUrl, Map.of(), body, type);
    }

    @Nullable
    protected <T> T executePutRequest(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @Nullable Object body,
        @NotNull Type type
    ) throws DBException {
        return executePutRequest(endpointUrl, parameters, body, MediaType.JSON, type);
    }

    @NotNull
    protected <T> T executePutRequestVal(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @Nullable Object body,
        @NotNull Type type
    ) throws DBException {
        return checkNotNullResult(endpointUrl, executePutRequest(endpointUrl, parameters, body, MediaType.JSON, type));
    }

    @Nullable
    protected <T> T executePutRequest(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @Nullable Object body,
        @NotNull MediaType bodyMediaType,
        @NotNull Type type
    ) throws DBException {
        return put(endpointUrl, parameters, body, bodyMediaType, type);
    }

    @NotNull
    protected <T> T executePutRequestVal(
        @NotNull String endpointUrl,
        @NotNull Map<String, ?> parameters,
        @Nullable Object body,
        @NotNull MediaType bodyMediaType,
        @NotNull Type type
    ) throws DBException {
        return checkNotNullResult(endpointUrl, executePutRequest(endpointUrl, parameters, body, bodyMediaType, type));
    }

    protected HttpRequest.BodyPublisher createBodyPublisher(
        @Nullable Object body,
        @NotNull MediaType mediaType
    ) {
        if (body == null) {
            return HttpRequest.BodyPublishers.noBody();
        }

        return switch (mediaType) {
            case JSON -> {
                String json = gson.toJson(body);
                yield HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
            }

            case TEXT, XML ->
                HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8);


            case OCTET_STREAM -> {
                if (!(body instanceof byte[] bytes)) {
                    throw new IllegalArgumentException("Body for OCTET_STREAM must be byte[]");
                }
                yield HttpRequest.BodyPublishers.ofByteArray(bytes);
            }
        };
    }

    private static boolean isValidURI(@NotNull String apiUrl) {
        URI base;
        try {
            base = URI.create(apiUrl);
        } catch (IllegalArgumentException e) {
            return false;
        }

        if (!"http".equalsIgnoreCase(base.getScheme())
            && !"https".equalsIgnoreCase(base.getScheme())) {
            return false;
        }

        return base.getHost() != null;
    }

    @NotNull
    protected URI buildUri(@NotNull String endpoint, @NotNull Map<String, ?> params) throws DBException {
        if (!isValidURI(apiUrl)) {
            throw new DBException("Invalid API URL: " + apiUrl);
        }

        StringBuilder sb = new StringBuilder(apiUrl);

        if (!apiUrl.endsWith("/")) {
            sb.append("/");
        }
        sb.append(encodePath(endpoint));

        if (!params.isEmpty()) {
            sb.append("?");
            params.forEach((k, v) -> {
                if (v == null) {
                    return;
                }
                if (v instanceof Collection<?> col) {
                    if (col.isEmpty()) {
                        appendQuery(sb, k, "");
                    } else {
                        for (Object o : col) {
                            appendQuery(sb, k, o.toString());
                        }
                    }
                } else if (v.getClass().isArray()) {
                    var array = (Object[]) v;
                    if (array.length == 0) {
                        appendQuery(sb, k, "");
                    } else {
                        for (Object o : array) {
                            appendQuery(sb, k, o.toString());
                        }
                    }
                } else {
                    appendQuery(sb, k, v.toString());
                }
            });
        }

        // Fix spaces encoding. Somehow they are not recognized
        String encodedUri = sb.toString();
        encodedUri = encodedUri.replace("+", "%20");

        return URI.create(encodedUri);
    }

    @NotNull
    protected <T> T execute(@NotNull HttpRequest.Builder builder, @NotNull Type type) throws DBException {
        HttpRequest request = builder
            .timeout(Duration.ofMillis(readTimeoutMs))
            .build();

        URI uri = request.uri();

        logDebug("--> RPC call: " + uri.getPath());

        HttpRequestWrapper reqWrapper = new HttpRequestWrapper(request);

        InterceptorChain chain = new InterceptorChain(
            interceptors,
            0,
            httpClient,
            reqWrapper,
            type,
            readTimeoutMs
        );

        try {
            HttpResponseWrapper responseWrapper = executeChain(chain, reqWrapper, uri);

            return processResponse(type, uri, responseWrapper);
        } catch (Throwable e) {
            if (e instanceof ExecutionException ee) {
                e = ee.getCause();
            }
            for (RestExceptionHandler handler : exceptionHandlers) {
                handler.handle(e);
            }

            String message = e instanceof DBException && e.getMessage() != null ?
                e.getMessage() : "Error connecting to " + getLoggableUri(uri);
            handleRequestException(message, e);

            throw new DBException(message, e);

        } finally {
            long remoteMs = TimeUnit.NANOSECONDS.toMillis(reqWrapper.getRemoteElapsedNanos());
            logDebug("\t<-- Call finished [" + uri.getPath() + "] (" + remoteMs + "ms)");
        }
    }

    @NotNull
    protected HttpResponseWrapper executeChain(
        @NotNull InterceptorChain chain,
        @NotNull HttpRequestWrapper request,
        @NotNull URI uri
    ) throws Exception {
        return chain.proceed(request);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private <T> T processResponse(
        @NotNull Type type,
        @NotNull URI uri,
        @NotNull HttpResponseWrapper resp
    ) throws DBException {

        int code = resp.code();

        if (!isSuccessful(code)) {

            String message;

            if (code == 404) {
                message = "Endpoint '" + getLoggableUri(uri) + "' not recognized by remote server";
            } else {
                String body = resp.bodyString();
                message = CommonUtils.isEmpty(body)
                    ? ("Error processing HTTP request: HTTP " + code)
                    : body;
            }

            throw mapErrorResponse(code, message, uri);
        }

        if (type == InputStream.class) {
            return (T) resp.bodyStream();
        }

        if (type == byte[].class) {
            return (T) resp.bodyBytes();
        }

        if (type == String.class) {
            return (T) resp.bodyString();
        }

        String bodyString = resp.bodyString();
        return gson.fromJson(bodyString, type);
    }

    @NotNull
    protected DBException mapErrorResponse(int code, @NotNull String message, @NotNull URI uri) {
        logError("Failed to execute request " + getLoggableUri(uri) + " - " + message);
        return new DBException(message);
    }

    /**
     * Returns a URI suitable for logs and user-facing error messages.
     * Query parameters and fragments may contain sensitive data.
     */
    @NotNull
    protected static String getLoggableUri(@NotNull URI uri) {
        String value = uri.toString();
        int queryIndex = value.indexOf('?');
        int fragmentIndex = value.indexOf('#');

        int endIndex;
        if (queryIndex < 0) {
            endIndex = fragmentIndex;
        } else if (fragmentIndex < 0) {
            endIndex = queryIndex;
        } else {
            endIndex = Math.min(queryIndex, fragmentIndex);
        }
        return endIndex < 0 ? value : value.substring(0, endIndex);
    }

    protected void logDebug(@NotNull String message) {
        log.fine(message);
    }

    protected void logError(@NotNull String message) {
        log.severe(message);
    }

    private boolean isSuccessful(int code) {
        return code >= 200 && code < 300;
    }

    protected void handleRequestException(@NotNull String message, @NotNull Throwable e) throws DBException {
        if (e instanceof DBException dbe) {
            throw dbe;
        }
        throw new DBException(message, e);
    }

    private static void appendQuery(@NotNull StringBuilder sb, @NotNull String key, @NotNull String val) {
        if (sb.charAt(sb.length() - 1) != '?') {
            sb.append("&");
        }
        sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
            .append("=")
            .append(URLEncoder.encode(val, StandardCharsets.UTF_8));
    }

    @NotNull
    private static String encodePath(@NotNull String path) {
        StringBuilder out = new StringBuilder();
        boolean first = true;

        for (String segment : path.split("/")) {
            if (!first) {
                out.append("/");
            }
            out.append(URLEncoder.encode(segment, StandardCharsets.UTF_8));
            first = false;
        }

        return out.toString();
    }
}
