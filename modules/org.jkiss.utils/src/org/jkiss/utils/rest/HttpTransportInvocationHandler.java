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
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.HttpConstants;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;

public abstract class HttpTransportInvocationHandler extends RpcInvocationHandler {

    private static final Pattern ST_LINE_PATTERN = Pattern.compile("\\s*at\\s+([\\w/.$]+)\\((.+)\\)");

    private final ExecutorService httpExecutor;
    private final HttpClient client;
    private final Map<String, String> headers;

    protected HttpTransportInvocationHandler(
        @NotNull Class<?> clientClass,
        @NotNull URI uri,
        @NotNull Gson gson,
        @NotNull String userAgent,
        @Nullable SSLContext sslContext,
        @NotNull Map<String, String> headers
    ) {
        super(clientClass, uri, gson, userAgent);
        this.httpExecutor = Executors.newSingleThreadExecutor();
        var clientBuilder = HttpClient.newBuilder()
            .executor(httpExecutor)
            .cookieHandler(new CookieManager());
        if (sslContext != null) {
            clientBuilder.sslContext(sslContext);
        }
        this.client = clientBuilder.build();
        this.headers = headers;
    }

    protected String invokeRemoteMethodOverHttp(
        @NotNull URI methodURI,
        @NotNull String requestString,
        RequestMapping methodMapping
    ) throws IOException, InterruptedException {
        HttpResponse.BodyHandler<String> readerBodyHandler =
            info -> HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);

        final HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(methodURI)
            .header(HttpConstants.HEADER_CONTENT_TYPE, HttpConstants.CONTENT_TYPE_JSON)
            .header(HttpConstants.HEADER_USER_AGENT, userAgent)
            .POST(HttpRequest.BodyPublishers.ofString(requestString));

        if (methodMapping != null && methodMapping.timeout() > 0) {
            builder.timeout(Duration.ofSeconds(methodMapping.timeout()));
        }

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        final HttpResponse<String> response = client.send(
            builder.build(),
            readerBodyHandler
        );

        String contents = response.body();
        if (response.statusCode() != RpcConstants.SC_OK) {
            handleHttpError(contents);
        }

        return contents;
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

    protected void handleHttpError(String contents) throws RpcException {
        if (contents.startsWith("<")) {
            // Seems to be html error page
//            Matcher matcher = Pattern.compile("<title>(.+)</title>").matcher(contents);
//            if (matcher.find()) {
//                throw new RestException("Server error: " + matcher.group(1));
//            }
            throw new RpcException(contents);
        }
        String[] stackTraceRows = contents.split("\n");
        StringBuilder errorLineBuilder = new StringBuilder(stackTraceRows[0]);
        List<StackTraceElement> stackTraceElements = new ArrayList<>();
        for (int i = 1; i < stackTraceRows.length; i++) {
            Matcher matcher = ST_LINE_PATTERN.matcher(stackTraceRows[i]);
            if (matcher.find()) {
                String methodRef = matcher.group(1);
                int divPos = methodRef.lastIndexOf('.');
                String className = methodRef.substring(0, divPos);
                String methodName = methodRef.substring(divPos + 1);

                String classRef = matcher.group(2);
                divPos = classRef.indexOf(':');
                String fileName;
                int fileLine;
                if (divPos == -1) {
                    fileName = classRef;
                    fileLine = -1;
                } else {
                    fileName = classRef.substring(0, divPos).trim();
                    fileLine = CommonUtils.toInt(classRef.substring(divPos + 1).trim());
                }
                stackTraceElements.add(
                    new StackTraceElement(className, methodName, fileName, fileLine));
            } else {
                // it may not be stack trace, but message with line separator
                errorLineBuilder.append(CommonUtils.getLineSeparator());
                errorLineBuilder.append(stackTraceRows[i]);
            }
        }
        RpcException runtimeException = new RpcException(errorLineBuilder.toString());
        Collections.addAll(stackTraceElements, runtimeException.getStackTrace());
        runtimeException.setStackTrace(stackTraceElements.toArray(new StackTraceElement[0]));

        throw runtimeException;
    }

}
