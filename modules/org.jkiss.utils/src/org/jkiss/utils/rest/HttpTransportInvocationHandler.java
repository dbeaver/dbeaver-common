/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp
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
package org.jkiss.utils.rest;

import com.google.gson.Gson;
import org.jkiss.code.NotNull;
import org.jkiss.utils.CommonUtils;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class HttpTransportInvocationHandler extends RpcInvocationHandler {

    private static final Pattern ST_LINE_PATTERN = Pattern.compile("\\s*at\\s+([\\w/.$]+)\\((.+)\\)");

    private final ExecutorService httpExecutor;
    private final HttpClient client;

    protected HttpTransportInvocationHandler(
        @NotNull Class<?> clientClass,
        @NotNull URI uri,
        @NotNull Gson gson,
        @NotNull String userAgent
    ) {
        super(clientClass, uri, gson, userAgent);
        this.httpExecutor = Executors.newSingleThreadExecutor();
        this.client = HttpClient.newBuilder()
            .executor(httpExecutor)
            .cookieHandler(new CookieManager())
            .build();
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
            .header("Content-Type", "application/json")
            .header("User-Agent", userAgent)
            .POST(HttpRequest.BodyPublishers.ofString(requestString));

        if (methodMapping != null && methodMapping.timeout() > 0) {
            builder.timeout(Duration.ofSeconds(methodMapping.timeout()));
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
