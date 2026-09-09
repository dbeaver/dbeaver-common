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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jkiss.code.NotNull;
import org.jkiss.utils.HttpConstants;
import org.jkiss.utils.IOUtils;
import org.jkiss.utils.oauth.code.OAuthRequestURLBuilder;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class OAuthUtils {

    public static final int TOKEN_VERIFIER_BYTE_LENGTH = 64;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @NotNull
    public static String generateCodeVerifier() {
        return generateRandomUrlSafeValue(TOKEN_VERIFIER_BYTE_LENGTH);
    }

    @NotNull
    public static String generateRandomUrlSafeValue(int byteLength) {
        if (byteLength <= 0) {
            throw new IllegalArgumentException("Byte length must be positive");
        }
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @NotNull
    public static String generateCodeChallenge(@NotNull String verifier) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Missing SHA-256 algorithm", e);
        }
    }

    public static OAuthTokens refreshAccessToken(
        @NotNull String tokenEndpoint,
        @NotNull String clientId,
        @NotNull String refreshToken,
        @NotNull String scope,
        int timeoutSec
    ) throws IOException {

        Map<String, String> tokenParams = new HashMap<>();
        tokenParams.put(OAuthConstants.PARAM_GRANT_TYPE, OAuthConstants.GRANT_TYPE_REFRESH_TOKEN);
        tokenParams.put(OAuthConstants.AUTH_PROP_CLIENT_ID, clientId);
        tokenParams.put(OAuthConstants.RESPONSE_PARAM_REFRESH_TOKEN, refreshToken);
        tokenParams.put(OAuthConstants.PARAM_SCOPE, scope);

        String tokenBody = OAuthRequestURLBuilder.buildURLParameters(tokenParams);
        HttpResponse<String> response = executePostRequest(tokenEndpoint, tokenBody, timeoutSec);
        if (response.statusCode() == HttpConstants.CODE_OK) {
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
            IOUtils.tryClose(client);
        }
    }
}
