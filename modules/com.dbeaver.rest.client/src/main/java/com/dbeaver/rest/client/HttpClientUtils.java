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

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

public abstract class HttpClientUtils {
    private static final Logger log = Logger.getLogger(HttpClientUtils.class.getName());

    private static final X509TrustManager[] NON_VALIDATING_TRUST_MANAGERS = new X509TrustManager[]{
        new X509ExtendedTrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {

            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            }

        }
    };

    private static SSLContext nonValidatingSslContext;

    @Nullable
    public static SSLContext initCustomSslContext() {
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, factory.getTrustManagers(), null);
            return ssl;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to init SSL context, fallback to default", e);
            return null;
        }
    }

    @NotNull
    public static SSLContext getNonValidatingSslContext() throws NoSuchAlgorithmException, KeyManagementException {
        if (nonValidatingSslContext == null) {
            nonValidatingSslContext = SSLContext.getInstance("TLS");
            nonValidatingSslContext.init(null, NON_VALIDATING_TRUST_MANAGERS, new SecureRandom());
        }
        return nonValidatingSslContext;
    }

    @Nullable
    public static SSLContext createSSLContext() {
        if (!AbstractRestClient.DISABLE_SSL_CERT_VALIDATION) {
            return initCustomSslContext();
        }

        try {
            return getNonValidatingSslContext();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
