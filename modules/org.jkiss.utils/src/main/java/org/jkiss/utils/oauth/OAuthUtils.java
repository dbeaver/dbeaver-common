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
package org.jkiss.utils.oauth;

import com.google.gson.*;
import org.jkiss.code.NotNull;
import org.jkiss.utils.HttpConstants;
import org.jkiss.utils.oauth.code.OAuthRequestURLBuilder;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class OAuthUtils {
    private static final Gson gson = new GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .setPrettyPrinting()
        .create();

    public static OAuthTokens refreshAccessToken(
        @NotNull String tokenEndpoint,
        @NotNull String clientId,
        @NotNull String refreshToken,
        @NotNull String scope,
        int timeoutSec
    ) throws IOException {

        Map<String, String> tokenParams = new HashMap<>();
        tokenParams.put("grant_type", "refresh_token");
        tokenParams.put("client_id", clientId);
        tokenParams.put("refresh_token", refreshToken);
        tokenParams.put("scope", scope);

        String tokenBody = OAuthRequestURLBuilder.buildURLParameters(tokenParams);
        HttpResponse<String> response = executePostRequest(tokenEndpoint, tokenBody, timeoutSec);
        if (response.statusCode() == 200) {
            JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
            if (jsonObject.has(OAuthConstants.RESPONSE_PARAM_ACCESS_TOKEN)) {
                String accessToken = jsonObject.get(OAuthConstants.RESPONSE_PARAM_ACCESS_TOKEN).getAsString();
                String newRefreshToken = jsonObject.has(OAuthConstants.RESPONSE_PARAM_REFRESH_TOKEN)
                    ? jsonObject.get(OAuthConstants.RESPONSE_PARAM_REFRESH_TOKEN).getAsString() : refreshToken;
                return new OAuthTokens(accessToken, newRefreshToken);
            }
        }
        throw new IOException("Failed to refresh access token. HTTP status: " + response.statusCode() + ", body: " + response.body());
    }

    public static HttpResponse<String> executePostRequest(
        @NotNull String endpoint,
        @NotNull String body,
        int timeoutSec
    ) throws IOException {
        HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header(HttpConstants.HEADER_CONTENT_TYPE, HttpConstants.CONTENT_TYPE_APP_FORM)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            try {
                return client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while requesting token", e);
            }
        } finally {
            if (client instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) client).close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
}
