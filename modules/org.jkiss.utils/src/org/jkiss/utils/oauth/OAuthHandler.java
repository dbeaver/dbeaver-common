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
package org.jkiss.utils.oauth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.utils.CommonUtils;

import java.awt.*;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class OAuthHandler {
    protected static final Gson gson = new GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .setPrettyPrinting()
        .create();
    public static final int TOKEN_VERIFIER_BYTE_LENGTH = 64;

    @NotNull
    private final String clientId;
    @Nullable
    private final String secretId;
    @NotNull
    private final String authUrl;
    @NotNull
    private final String tokenURL;
    private final int callbackPort;
    private int timeout = OAuthConstants.AUTH_DEFAULT_SSO_TIMEOUT;
    @NotNull
    private String callbackEndpoint = OAuthConstants.DEFAULT_CALLBACK_ENDPOINT;
    @Nullable
    private String codeChallenge;

    public OAuthHandler(
        @NotNull String clientId,
        @Nullable String secretId,
        @NotNull String authUrl,
        @NotNull String tokenURL,
        int callbackPort
    ) {
        this.clientId = clientId;
        this.secretId = secretId;
        this.authUrl = authUrl;
        this.tokenURL = tokenURL;
        this.callbackPort = callbackPort;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void setCallbackEndpoint(@NotNull String callbackEndpoint) {
        this.callbackEndpoint = callbackEndpoint;
    }

    public String authorize() throws IOException {
        try (OAuthResponseHandler handler = new OAuthResponseHandler(callbackPort, callbackEndpoint)) {
            String verifier = generateCodeChallengeAndVerifier();
            startSSO(handler);
            String code = handler.requestCode().get(timeout, TimeUnit.SECONDS);

            HttpRequest.Builder postBuilder = HttpRequest.newBuilder().uri(URI.create(tokenURL));
            postBuilder.header("Content-type", "application/x-www-form-urlencoded");
            postBuilder.POST(HttpRequest.BodyPublishers.ofString(createTokenRequestParameters(
                code,
                verifier
            )));
            postBuilder.timeout(Duration.ofSeconds(timeout));
            HttpRequest postRequest = postBuilder.build();
            handler.addStabContext();
            HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .version(HttpClient.Version.HTTP_2).build();
            HttpResponse<String> response = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Error getting token info " + response.body());
            }
            return extractResponse(response);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IOException(e);
        }
    }

    @NotNull
    protected static String extractResponse(HttpResponse<String> response) throws IOException {
        OAuthResponseDTO authResponseDTO = gson.fromJson(response.body(), OAuthResponseDTO.class);
        if (authResponseDTO.id_token() != null) {
            return authResponseDTO.id_token();
        } else {
            throw new IOException("Error extracting token");
        }
    }

    private void startSSO(@NotNull OAuthResponseHandler handler) throws IOException {
        handler.initServer();
        createBrowser(buildAuthUrl());
    }

    protected void createBrowser(@NotNull String url) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(url));
        } else {
            throw new IOException("Desktop BROWSER interface is not supported");
        }
    }

    @NotNull
    private String generateCodeChallengeAndVerifier() throws IOException {
        String codeVerifier = generateVerifier();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] shaEncode = digest.digest(codeVerifier.getBytes());
            codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(shaEncode);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Missing SHA-256 algorithm");
        }
        return codeVerifier;
    }

    @NotNull
    private static String generateVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] secureValue = new byte[TOKEN_VERIFIER_BYTE_LENGTH];
        secureRandom.nextBytes(secureValue);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secureValue);
    }

    @NotNull
    private String createTokenRequestParameters(
        @NotNull String code,
        @NotNull String verifier
    ) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "authorization_code");
        parameters.put("code", code);
        parameters.put(OAuthConstants.AUTH_PROP_CLIENT_ID, clientId);
        if (CommonUtils.isNotEmpty(secretId)) {
            parameters.put(OAuthConstants.AUTH_PROP_CLIENT_SECRET, secretId);
        }
        parameters.put("code_verifier", verifier);
        parameters.put(
            "redirect_uri",
            String.format(OAuthConstants.AUTH_SSO_CALLBACK_TEMPLATE, callbackPort, callbackEndpoint)
        );
        return parameters.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
    }

    protected String buildAuthUrl() throws IOException {
        return new OAuthRequestURLBuilder(authUrl)
            .withClientId(clientId)
            .withRedirectURI(String.format(OAuthConstants.AUTH_SSO_CALLBACK_TEMPLATE, callbackPort, callbackEndpoint))
            .withCodeChallenge(codeChallenge)
            .build();
    }
}
