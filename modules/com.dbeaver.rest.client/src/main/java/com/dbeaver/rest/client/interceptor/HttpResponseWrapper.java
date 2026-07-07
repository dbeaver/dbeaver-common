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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class HttpResponseWrapper {

    private final int code;
    private final HttpHeaders headers;
    private final byte[] bytesBody;
    private final InputStream streamBody;

    public HttpResponseWrapper(HttpResponse<?> response) {
        this.code = response.statusCode();
        this.headers = response.headers();

        Object body = response.body();

        if (body instanceof byte[] b) {
            this.bytesBody = b;
            this.streamBody = null;
        } else if (body instanceof InputStream s) {
            this.streamBody = s;
            this.bytesBody = null;
        } else {
            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            this.bytesBody = data;
            this.streamBody = new ByteArrayInputStream(data);
        }
    }

    public int code() {
        return code;
    }

    public Map<String, List<String>> headers() {
        return headers.map();
    }

    public byte[] bodyBytes() {
        if (bytesBody != null) {
            return bytesBody;
        }
        try {
            return streamBody.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public InputStream bodyStream() {
        if (streamBody != null) {
            return streamBody;
        }
        return new ByteArrayInputStream(bytesBody);
    }

    public String bodyString() {
        return new String(bodyBytes(), StandardCharsets.UTF_8);
    }
}