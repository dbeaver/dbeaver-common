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
package org.jkiss.utils;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.util.List;
import java.util.Map;

public class WSClientUtils {
    private static final String HTTP_PROXY_HOST = "http.proxyHost";
    private static final String HTTP_PROXY_PORT = "http.proxyPort";
    private static final String HTTPS_PROXY_HOST = "https.proxyHost";
    private static final String HTTPS_PROXY_PORT = "https.proxyPort";

    @Nullable
    public static List<String> getHeaders(@Nullable Map<String, List<String>> allHeaders, @NotNull String headerName) {
        if (allHeaders == null) {
            return null;
        }
        List<String> headerValues = allHeaders.get(headerName);
        if (headerValues == null) {
            headerValues = allHeaders.get(headerName.toLowerCase());
        }
        return headerValues;
    }

    @NotNull
    public static ProxyInfo findProxyInfo() {
        String host = System.getProperty(HTTP_PROXY_HOST);
        if (CommonUtils.isEmpty(host)) {
            host = System.getProperty(HTTPS_PROXY_HOST);
        }
        int port = CommonUtils.toInt(System.getProperty(HTTP_PROXY_PORT), -1);
        if (port < 0) {
            port = CommonUtils.toInt(System.getProperty(HTTPS_PROXY_PORT), -1);
        }

        return new ProxyInfo(host, port);
    }

    public static class ProxyInfo {
        @Nullable
        private final String host;
        private final int port;

        public ProxyInfo(@Nullable String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Nullable
        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public boolean exists() {
            return CommonUtils.isNotEmpty(host) && port >= 0;
        }
    }
}
