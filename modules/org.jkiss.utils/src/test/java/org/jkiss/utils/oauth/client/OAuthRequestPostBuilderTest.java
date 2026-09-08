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
package org.jkiss.utils.oauth.client;

import org.jkiss.utils.HttpConstants;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OAuthRequestPostBuilderTest {
    @Test
    void buildsClientCredentialsRequest() {
        HttpRequest request = new OAuthRequestPostBuilder("https://example.com/token")
            .withClientId("client")
            .withClientSecret(null)
            .withGrantType("client_credentials")
            .withTimeout(30)
            .build();

        assertEquals(29, request.bodyPublisher().orElseThrow().contentLength());
        assertEquals("Basic Y2xpZW50Og==", request.headers().firstValue(HttpConstants.HEADER_AUTHORIZATION).orElseThrow());
        assertEquals(Duration.ofSeconds(30), request.timeout().orElseThrow());
    }
}
