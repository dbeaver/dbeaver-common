/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
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

import com.google.gson.*;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.utils.oauth.IOAuthHandler;
import org.jkiss.utils.oauth.OAuthConstants;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles OAuth 2.0 Client Credentials Flow.
 * This class is designed to manage the OAuth client credentials flow,
 * which is typically used for server-to-server communication
 * without user interaction.
 */
public class OAuthClientCredentialsHandler implements IOAuthHandler {
    protected static final Gson gson = new GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .setPrettyPrinting()
        .create();

    @NotNull
    protected final String clientId;
    @Nullable
    protected final String secretId;

    @NotNull
    protected final String authUrl;
    protected int timeout = OAuthConstants.AUTH_DEFAULT_SSO_TIMEOUT;

    /**
     * Constructs an OAuthHandler with required parameters.
     *
     * @param clientId     the OAuth client ID
     * @param secretId     the OAuth client secret (nullable for PKCE-only flows)
     * @param authUrl      the authorization endpoint URL
     */
    public OAuthClientCredentialsHandler(
        @NotNull String clientId,
        @Nullable String secretId,
        @NotNull String authUrl
    ) {
        this.clientId = clientId;
        this.secretId = secretId;
        this.authUrl = authUrl;
    }


    @Override
    public Map<String, String> authorize() throws IOException {
        try (HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(OAuthConstants.AUTH_DEFAULT_SSO_TIMEOUT))
            .build()) {
            OAuthRequestPostBuilder requestBuilder = new OAuthRequestPostBuilder(authUrl)
                .withClientId(clientId)
                .withClientSecret(secretId)
                .withGrantType(OAuthConstants.GRANT_TYPE_CLIENT_CREDENTIALS);
            // Send POST
            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return extractResponse(response);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    protected Map<String, String> extractResponse(HttpResponse<String> response) throws IOException {
        JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
        String idToken = jsonObject.get(OAuthConstants.RESULT_PROP_TOKEN_ID).getAsString();
        if (idToken != null) {
            Map<String, String> result = new HashMap<>();
            result.put(OAuthConstants.AUTH_PROP_TOKEN, idToken);
            return result;
        } else {
            throw new IOException("Error extracting token");
        }
    }


}
