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
package com.dbeaver.rest.client.interceptor;

import org.jkiss.code.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class InterceptorChain implements HttpInterceptor.Chain {

    private final List<HttpInterceptor> interceptors;
    private final int index;
    private final HttpClient httpClient;
    private final HttpRequestWrapper request;
    private final Type expectedType;
    private final int readTimeoutMs;

    private volatile CompletableFuture<? extends HttpResponse<?>> future;

    public InterceptorChain(
        @NotNull List<HttpInterceptor> interceptors,
        int index,
        @NotNull HttpClient httpClient,
        @NotNull HttpRequestWrapper request,
        @NotNull Type expectedType,
        int readTimeoutMs
    ) {
        this.interceptors = interceptors;
        this.index = index;
        this.httpClient = httpClient;
        this.request = request;
        this.expectedType = expectedType;
        this.readTimeoutMs = readTimeoutMs;
    }

    @NotNull
    @Override
    public HttpRequestWrapper request() {
        return request;
    }

    @NotNull
    @Override
    public HttpResponseWrapper proceed(@NotNull HttpRequestWrapper req) throws IOException, InterruptedException {

        if (index < interceptors.size()) {
            HttpInterceptor next = interceptors.get(index);

            InterceptorChain nextChain = new InterceptorChain(
                interceptors,
                index + 1,
                httpClient,
                req,
                expectedType,
                readTimeoutMs
            );

            return next.intercept(nextChain);
        }

        return executeRequest(req.build());
    }

    @Override
    public void cancel() {
        CompletableFuture<?> f = future;
        if (f != null) {
            f.cancel(true);
        }
    }

    private HttpResponse.BodyHandler<?> chooseHandler() {
        return (expectedType == InputStream.class)
            ? HttpResponse.BodyHandlers.ofInputStream()
            : HttpResponse.BodyHandlers.ofByteArray();
    }

    @NotNull
    private HttpResponseWrapper executeRequest(@NotNull HttpRequest jdkRequest) throws IOException {

        HttpResponse.BodyHandler<?> handler = chooseHandler();

        long started = System.nanoTime();
        CompletableFuture<? extends HttpResponse<?>> f = httpClient.sendAsync(jdkRequest, handler);

        this.future = f;

        try {
            HttpResponse<?> raw = f.get(readTimeoutMs, TimeUnit.MILLISECONDS);

            return new HttpResponseWrapper(raw);

        } catch (TimeoutException e) {

            f.cancel(true);
            throw new IOException("HTTP read timeout after " + readTimeoutMs + " ms", e);

        } catch (ExecutionException e) {

            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }

            throw new IOException(cause);

        } catch (InterruptedException e) {
            // Restore interrupt status since we don't propagate InterruptedException
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } finally {
            request.addRemoteElapsedNanos(System.nanoTime() - started);
        }
    }
}
