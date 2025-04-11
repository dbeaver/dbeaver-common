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

import com.google.gson.*;
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

/**
 * Handles the OAuth 2.0 Authorization Code Flow including PKCE (Proof Key for Code Exchange).
 * It generates a code verifier and challenge, launches a browser for user authentication,
 * and exchanges the authorization code for an access token.
 * This class supports customization of timeout and callback endpoint.
 */
public class OAuthHandler {
    protected static final Gson gson = new GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .setPrettyPrinting()
        .create();
    public static final int TOKEN_VERIFIER_BYTE_LENGTH = 64;

    @NotNull
    protected final String clientId;
    @Nullable
    protected final String secretId;
    @NotNull
    protected final String authUrl;
    @NotNull
    protected final String tokenURL;
    protected final int callbackPort;
    protected int timeout = OAuthConstants.AUTH_DEFAULT_SSO_TIMEOUT;
    @NotNull
    protected String callbackEndpoint = OAuthConstants.DEFAULT_CALLBACK_ENDPOINT;
    @Nullable
    protected String codeChallenge;

    /**
     * Constructs an OAuthHandler with required parameters.
     *
     * @param clientId     the OAuth client ID
     * @param secretId     the OAuth client secret (nullable for PKCE-only flows)
     * @param authUrl      the authorization endpoint URL
     * @param tokenURL     the token exchange endpoint URL
     * @param callbackPort the port on which the temporary server will listen for the callback
     */
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

    /**
     * Sets the timeout (in seconds) to wait for the OAuth callback response.
     *
     * @param timeout in seconds
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * Sets a custom callback endpoint path (e.g. "/callback").
     *
     * @param callbackEndpoint path part of the redirect URI
     */
    public void setCallbackEndpoint(@NotNull String callbackEndpoint) {
        this.callbackEndpoint = callbackEndpoint;
    }

    /**
     * Executes the full OAuth authorization code flow and retrieves the access token.
     *
     * @return a map containing the token information (e.g. id_token)
     * @throws IOException in case of HTTP failure, timeout, or invalid responses
     */
    public Map<String, String> authorize() throws IOException {
        try (OAuthResponseHandler handler = new OAuthResponseHandler(callbackPort, callbackEndpoint)) {
            String verifier = generateCodeChallengeAndVerifier();
            startSSO(handler);
            String code = handler.requestCode().get(timeout, TimeUnit.SECONDS);

            HttpRequest.Builder postBuilder = HttpRequest.newBuilder().uri(URI.create(tokenURL));
            postBuilder.header("Content-type", "application/x-www-form-urlencoded");
            postBuilder.POST(HttpRequest.BodyPublishers.ofString(createTokenRequestParameters(code, verifier)));
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

    /**
     * Parses the token response and extracts relevant token values.
     *
     * @param response HTTP response from the token endpoint
     * @return a map with extracted token(s)
     * @throws IOException if the response does not contain expected data
     */
    @NotNull
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

    /**
     * Starts the SSO process by launching the default browser to the authorization URL.
     *
     * @param handler OAuth response handler
     * @throws IOException if the desktop browser cannot be launched
     */
    private void startSSO(@NotNull OAuthResponseHandler handler) throws IOException {
        handler.initServer();
        createBrowser(buildAuthUrl());
    }

    /**
     * Launches the system default browser with the given authorization URL.
     *
     * @param url the URL to open
     * @throws IOException if Desktop API is not supported
     */
    protected void createBrowser(@NotNull String url) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(url));
        } else {
            throw new IOException("Desktop BROWSER interface is not supported");
        }
    }

    /**
     * Generates a code verifier and corresponding code challenge using SHA-256.
     *
     * @return the code verifier
     * @throws IOException if SHA-256 algorithm is not available
     */
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

    /**
     * Generates a random code verifier as a URL-safe Base64 string.
     *
     * @return a new code verifier
     */
    @NotNull
    private static String generateVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] secureValue = new byte[TOKEN_VERIFIER_BYTE_LENGTH];
        secureRandom.nextBytes(secureValue);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secureValue);
    }

    /**
     * Builds the form parameters for the token request including verifier and redirect URI.
     *
     * @param code     the authorization code received from the server
     * @param verifier the original code verifier used in PKCE
     * @return URL-encoded form parameters for token request
     */
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
        return OAuthRequestURLBuilder.buildURLParameters(parameters);
    }

    /**
     * Builds the full authorization URL with parameters.
     *
     * @return the full URL to initiate OAuth authorization
     * @throws IOException if URL cannot be constructed
     */
    protected String buildAuthUrl() throws IOException {
        return new OAuthRequestURLBuilder(authUrl)
            .withClientId(clientId)
            .withRedirectURI(String.format(OAuthConstants.AUTH_SSO_CALLBACK_TEMPLATE, callbackPort, callbackEndpoint))
            .withCodeChallenge(codeChallenge)
            .build();
    }
}
