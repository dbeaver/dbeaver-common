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

import java.io.IOException;

public interface HttpInterceptor {

    HttpResponseWrapper intercept(Chain chain) throws IOException, InterruptedException;

    interface Chain {
        HttpRequestWrapper request();

        HttpResponseWrapper proceed(HttpRequestWrapper request) throws IOException, InterruptedException;

        default void cancel() {
        }

        default int connectTimeoutMillis() {
            throw new UnsupportedOperationException("connectTimeoutMillis is not supported");
        }

        default int readTimeoutMillis() {
            throw new UnsupportedOperationException("readTimeoutMillis is not supported");
        }

        default int writeTimeoutMillis() {
            throw new UnsupportedOperationException("writeTimeoutMillis is not supported");
        }

        default Chain withConnectTimeout(int timeoutMillis) {
            throw new UnsupportedOperationException("withConnectTimeout is not supported");
        }

        default Chain withReadTimeout(int timeoutMillis) {
            throw new UnsupportedOperationException("withReadTimeout is not supported");
        }

        default Chain withWriteTimeout(int timeoutMillis) {
            throw new UnsupportedOperationException("withWriteTimeout is not supported");
        }
    }
}