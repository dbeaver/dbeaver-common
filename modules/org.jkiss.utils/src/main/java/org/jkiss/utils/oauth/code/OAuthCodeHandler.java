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

import com.google.gson.*;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.HttpConstants;
import org.jkiss.utils.HttpUtils;
import org.jkiss.utils.IOUtils;
import org.jkiss.utils.oauth.IOAuthHandler;
import org.jkiss.utils.oauth.OAuthConstants;
import org.jkiss.utils.oauth.OAuthUtils;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Handles the OAuth 2.0 Authorization Code Flow including PKCE (Proof Key for Code Exchange).
 * It generates a code verifier and challenge, launches a browser for user authentication,
 * and exchanges the authorization code for an access token.
 * This class supports customization of timeout and callback endpoint.
 */
public class OAuthCodeHandler implements IOAuthHandler {
    protected static final Gson gson = new GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .setPrettyPrinting()
        .create();
    private static final int CANCEL_POLL_INTERVAL_MILLIS = 100;

    @Nullable
    private String resolvedRedirectUri;

    @NotNull
    protected final String clientId;
    @Nullable
    protected final String secretId;
    @NotNull
    protected final String authUrl;
    @NotNull
    protected final String tokenURL;
    @NotNull
    protected final String redirectUri;
    protected final int callbackPort;
    @NotNull
    protected final String callbackEndpoint;
    protected int timeout = OAuthConstants.AUTH_DEFAULT_SSO_TIMEOUT;
    @Nullable
    protected String state;
    private boolean generatedState;

    @Nullable
    protected final String scope;

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
    public OAuthCodeHandler(
        @NotNull String clientId,
        @Nullable String secretId,
        @NotNull String authUrl,
        @NotNull String tokenURL,
        @NotNull String callbackEndpoint,
        @NotNull String redirectUri,
        int callbackPort
    ) {
        this(clientId, secretId, authUrl, tokenURL, callbackEndpoint, redirectUri, callbackPort, null);
    }

    /**
     * Constructs an OAuthHandler with required parameters.
     *
     * @param clientId     the OAuth client ID
     * @param secretId     the OAuth client secret (nullable for PKCE-only flows)
     * @param authUrl      the authorization endpoint URL
     * @param tokenURL     the token exchange endpoint URL
     * @param callbackPort the port on which the temporary server will listen for the callback
     * @param scope        requested scope
     */
    public OAuthCodeHandler(
        @NotNull String clientId,
        @Nullable String secretId,
        @NotNull String authUrl,
        @NotNull String tokenURL,
        @NotNull String callbackEndpoint,
        @NotNull String redirectUri,
        int callbackPort,
        @Nullable String scope
    ) {
        this.clientId = clientId;
        this.secretId = secretId;
        this.authUrl = authUrl;
        this.tokenURL = tokenURL;
        this.callbackEndpoint = callbackEndpoint;
        this.callbackPort = callbackPort;
        this.redirectUri = redirectUri;
        this.scope = scope;
    }

    /**
     * Sets the timeout (in seconds) to wait for the OAuth callback response.
     *
     * @param timeout in seconds
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Nullable
    public String getState() {
        return state;
    }

    /**
     * Executes the full OAuth authorization code flow and retrieves the access token.
     *
     * @return a map containing the token information (e.g. id_token)
     * @throws IOException in case of HTTP failure, timeout, or invalid responses
     */
    @NotNull
    @Override
    public Map<String, String> authorize() throws IOException {
        try {
            return authorize(this::exchangeCode, () -> false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    /** Runs the browser flow with caller-owned token transport and cancellation. */
    @NotNull
    public <T> T authorize(@NotNull TokenExchanger<T> exchanger, @NotNull BooleanSupplier canceled)
        throws IOException, InterruptedException {
        if (canceled.getAsBoolean()) {
            throw new InterruptedException("OAuth authorization was canceled");
        }
        resolvedRedirectUri = null;
        if (state == null || generatedState) {
            state = OAuthUtils.generateRandomUrlSafeValue(32);
            generatedState = true;
        }
        try (IOAuthCodeResponseHandler handler = createCodeResponseHandler()) {
            String verifier = OAuthUtils.generateCodeVerifier();
            codeChallenge = OAuthUtils.generateCodeChallenge(verifier);
            startSSO(handler);
            String code = awaitCode(handler.requestCode(), canceled);
            if (canceled.getAsBoolean()) {
                throw new InterruptedException("OAuth authorization was canceled");
            }
            handler.addStabContext();
            return exchanger.exchange(code, verifier, getRedirectUri());
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException(e);
        }
    }

    @NotNull
    private String awaitCode(@NotNull Future<String> code, @NotNull BooleanSupplier canceled)
        throws InterruptedException, ExecutionException, TimeoutException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
        while (!canceled.getAsBoolean()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new TimeoutException("OAuth authorization was not completed in time");
            }
            try {
                return code.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(CANCEL_POLL_INTERVAL_MILLIS)), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                // Poll cancellation while waiting for the browser.
            }
        }
        throw new InterruptedException("OAuth authorization was canceled");
    }

    @NotNull
    protected Map<String, String> exchangeCode(@NotNull String code, @NotNull String verifier, @NotNull String callbackUri)
        throws IOException, InterruptedException {
        HttpRequest.Builder postBuilder = HttpRequest.newBuilder().uri(URI.create(tokenURL));
        postBuilder.headers(getHeaderParameters());
        postBuilder.POST(HttpRequest.BodyPublishers.ofString(createTokenRequestParameters(code, verifier, callbackUri)));
        postBuilder.timeout(Duration.ofSeconds(timeout));

        HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .version(HttpClient.Version.HTTP_2).build();
        try {
            HttpResponse<String> response = client.send(postBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpConstants.CODE_OK) {
                throw new IOException("Error getting token info " + response.body());
            }
            return extractResponse(response);
        } finally {
            IOUtils.tryClose(client);
        }
    }

    @FunctionalInterface
    public interface TokenExchanger<T> {
        @NotNull
        T exchange(@NotNull String code, @NotNull String verifier, @NotNull String redirectUri)
            throws IOException, InterruptedException;
    }

    @NotNull
    protected String[] getHeaderParameters() {
        return new String[] {HttpConstants.HEADER_CONTENT_TYPE, HttpConstants.CONTENT_TYPE_APP_FORM};
    }

    @NotNull
    protected IOAuthCodeResponseHandler createCodeResponseHandler() {
        return new OAuthCodeResponseHandler(callbackPort, callbackEndpoint, state);
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
    protected void startSSO(@NotNull IOAuthCodeResponseHandler handler) throws IOException {
        handler.initServer();
        if (URI.create(redirectUri).getPort() == 0) {
            URI callbackUri = handler.getCallbackUri();
            if (callbackUri != null) {
                resolvedRedirectUri = callbackUri.toString();
            }
        }
        String authorizationUrl = buildAuthUrl();
        URI uri = URI.create(authorizationUrl);
        if (!HttpUtils.parseQuery(uri.getRawQuery()).containsKey(OAuthConstants.PARAM_STATE)) {
            authorizationUrl += (uri.getRawQuery() == null ? "?" : "&")
                + OAuthRequestURLBuilder.buildURLParameters(Map.of(OAuthConstants.PARAM_STATE, state));
        }
        createBrowser(authorizationUrl);
    }

    /**
     * Launches the system default browser with the given authorization URL.
     *
     * @param url the URL to open
     * @throws IOException if Desktop API is not supported
     */
    protected void createBrowser(@NotNull String url) throws IOException {
        OAuthUtils.openBrowser(url);
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
        @NotNull String verifier,
        @NotNull String callbackUri
    ) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(OAuthConstants.PARAM_GRANT_TYPE, OAuthConstants.GRANT_TYPE_AUTH_CODE);
        parameters.put(OAuthConstants.PARAM_CODE, code);
        parameters.put(OAuthConstants.AUTH_PROP_CLIENT_ID, clientId);
        if (CommonUtils.isNotEmpty(secretId)) {
            parameters.put(OAuthConstants.AUTH_PROP_CLIENT_SECRET, secretId);
        }
        parameters.put(OAuthConstants.PARAM_CODE_VERIFIER, verifier);
        parameters.put(
            OAuthConstants.PARAM_REDIRECT_URI,
            callbackUri
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
        var builder = new OAuthRequestURLBuilder(authUrl)
            .withClientId(clientId)
            .withRedirectURI(getRedirectUri());
        if (codeChallenge != null) {
            builder.withCodeChallenge(codeChallenge);
        }
        if (CommonUtils.isNotEmpty(scope)) {
            builder.withScope(scope);
        }
        if (state != null) {
            builder.withState(state);
        }
        return builder.build();
    }

    @NotNull
    protected String getRedirectUri() {
        return resolvedRedirectUri == null ? redirectUri : resolvedRedirectUri;
    }

    @NotNull
    public static OAuthCodeHandlerBuilder<?> builder() {
        return new OAuthCodeHandlerBuilder<>();
    }

    public static class OAuthCodeHandlerBuilder<T extends OAuthCodeHandler> {
        protected static final String ENDPOINT_SLASH = "/";

        protected String clientId;
        protected String secretId;
        protected String authUrl;
        protected String tokenURL;
        protected String redirectUri;
        protected int callbackPort = 0;

        protected String scope;

        protected int timeout = OAuthConstants.AUTH_DEFAULT_SSO_TIMEOUT;
        protected String callbackEndpoint;
        protected String state;

        @NotNull
        public OAuthCodeHandlerBuilder<T> withClientId(@NotNull String clientId) {
            this.clientId = clientId;
            return this;
        }

        @NotNull
        public OAuthCodeHandlerBuilder<T> withSecretId(@Nullable String secretId) {
            this.secretId = secretId;
            return this;
        }

        @NotNull
        public OAuthCodeHandlerBuilder<T> withAuthUrl(@NotNull String authUrl) {
            this.authUrl = authUrl;
            return this;
        }

        @NotNull
        public OAuthCodeHandlerBuilder<T> withTokenUrl(@NotNull String tokenURL) {
            this.tokenURL = tokenURL;
            return this;
        }

        @NotNull
        public OAuthCodeHandlerBuilder<T> withCallbackPort(int port) {
            this.callbackPort = port;
            return this;
        }

        @NotNull
        public OAuthCodeHandlerBuilder<T> withTimeout(int seconds) {
            this.timeout = seconds;
            return this;
        }

        @NotNull
        public OAuthCodeHandlerBuilder<T> withCallbackEndpoint(@Nullable String endpoint) {
            if (endpoint != null) {
                this.callbackEndpoint = endpoint.startsWith(ENDPOINT_SLASH) ? endpoint : ENDPOINT_SLASH + endpoint;
            } else {
                this.callbackEndpoint = null;
            }
            return this;
        }

        @NotNull
        public OAuthCodeHandlerBuilder<T> withRedirectUri(@NotNull String redirectUri) {
            this.redirectUri = redirectUri;
            return this;
        }

        @NotNull
        public OAuthCodeHandlerBuilder<T> withScope(@NotNull String scope) {
            this.scope = scope;
            return this;
        }

        @NotNull
        public T build() {
            if (CommonUtils.isEmpty(clientId)) {
                throw new IllegalStateException("clientId is required");
            }
            if (CommonUtils.isEmpty(authUrl)) {
                throw new IllegalStateException("authUrl is required");
            }
            if (CommonUtils.isEmpty(tokenURL)) {
                throw new IllegalStateException("tokenURL is required");
            }
            if (CommonUtils.isEmpty(callbackEndpoint)) {
                callbackEndpoint = getDefaultEndpoint();
            }
            if (CommonUtils.isEmpty(redirectUri)) {
                this.redirectUri = String.format(OAuthConstants.AUTH_SSO_CALLBACK_TEMPLATE, callbackPort, callbackEndpoint);
            }

            T handler = createOAuthCodeHandler();

            if (timeout > 0) {
                handler.setTimeout(timeout);
            }

            return handler;
        }

        @NotNull
        protected String getDefaultEndpoint() {
            return OAuthConstants.DEFAULT_CALLBACK_ENDPOINT;
        }

        @NotNull
        protected T createOAuthCodeHandler() {
            //noinspection unchecked
            return (T) new OAuthCodeHandler(clientId, secretId, authUrl, tokenURL, callbackEndpoint, redirectUri, callbackPort, scope);
        }
    }
}
