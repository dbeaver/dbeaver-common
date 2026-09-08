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
package org.jkiss.utils.oauth.code;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OAuthCodeResponseHandlerTest {
    @Test
    void returnsAuthorizationCode() throws Exception {
        int port = getFreePort();
        try (OAuthCodeResponseHandler handler = new OAuthCodeResponseHandler(port, "/callback", "expected")) {
            handler.initServer();

            HttpResponse<String> response = sendCallback(port, "?code=auth-code&state=expected");

            assertEquals(200, response.statusCode());
            assertEquals("auth-code", handler.requestCode().get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void ignoresUnexpectedState() throws Exception {
        int port = getFreePort();
        try (OAuthCodeResponseHandler handler = new OAuthCodeResponseHandler(port, "/callback", "expected")) {
            handler.initServer();

            HttpResponse<String> rejected = sendCallback(port, "?code=stale-code&state=unexpected");
            sendCallback(port, "?code=auth-code&state=expected");

            assertEquals(400, rejected.statusCode());
            assertEquals("auth-code", handler.requestCode().get(1, TimeUnit.SECONDS));
        }
    }

    private static HttpResponse<String> sendCallback(int port, String query) throws Exception {
        return HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/callback" + query)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private static int getFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
