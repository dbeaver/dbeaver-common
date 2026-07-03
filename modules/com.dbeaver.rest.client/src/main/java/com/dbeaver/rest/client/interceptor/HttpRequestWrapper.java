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

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.*;

public class HttpRequestWrapper {

    private final URI uri;
    private final String method;
    private final HttpRequest.BodyPublisher bodyPublisher;
    private final Duration timeout;
    private long remoteElapsedNanos;

    private final Map<String, List<String>> headers = new LinkedHashMap<>();

    public HttpRequestWrapper(@NotNull HttpRequest request) {
        this.uri = request.uri();
        this.method = request.method();
        this.bodyPublisher = request.bodyPublisher()
            .orElse(HttpRequest.BodyPublishers.noBody());
        this.timeout = request.timeout().orElse(null);

        request.headers().map().forEach((k, vs) ->
            headers.put(k, new ArrayList<>(vs))
        );
    }

    @NotNull
    public HttpRequest build() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .method(method, bodyPublisher);

        if (timeout != null) {
            builder.timeout(timeout);
        }

        headers.forEach((k, vs) -> {
            for (String v : vs) {
                builder.header(k, v);
            }
        });

        return builder.build();
    }

    @NotNull
    public URI uri() {
        return uri;
    }

    @NotNull
    public String method() {
        return method;
    }

    @NotNull
    public Map<String, List<String>> headers() {
        return Collections.unmodifiableMap(headers);
    }

    @NotNull
    public HttpRequestWrapper withHeader(String name, String value) {
        headers.put(name, new ArrayList<>(List.of(value)));
        return this;
    }

    public void addRemoteElapsedNanos(long nanos) {
        remoteElapsedNanos += nanos;
    }

    public long getRemoteElapsedNanos() {
        return remoteElapsedNanos;
    }
}
