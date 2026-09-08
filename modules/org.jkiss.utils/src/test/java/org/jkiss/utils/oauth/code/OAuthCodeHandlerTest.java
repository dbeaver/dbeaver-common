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

import org.jkiss.code.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OAuthCodeHandlerTest {
    @Test
    void addsStateToCustomAuthorizationUrl() throws IOException {
        TestOAuthCodeHandler handler = new TestOAuthCodeHandler();

        handler.startSSO(new NoOpResponseHandler());

        assertEquals("https://example.com/authorize?custom=value&state=expected", handler.openedUrl);
    }

    private static class TestOAuthCodeHandler extends OAuthCodeHandler {
        private String openedUrl;

        private TestOAuthCodeHandler() {
            super(
                "client",
                null,
                "https://example.com/authorize",
                "https://example.com/token",
                "/callback",
                "http://localhost/callback",
                0
            );
            state = "expected";
        }

        @Override
        protected String buildAuthUrl() {
            return "https://example.com/authorize?custom=value";
        }

        @Override
        protected void createBrowser(@NotNull String url) {
            openedUrl = url;
        }
    }

    private static class NoOpResponseHandler implements IOAuthCodeResponseHandler {
        @Override
        public void initServer() {
        }

        @NotNull
        @Override
        public Future<String> requestCode() {
            return new CompletableFuture<>();
        }

        @Override
        public void addStabContext() {
        }

        @Override
        public void close() {
        }
    }
}
