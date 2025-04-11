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

import org.jkiss.code.NotNull;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * OAuthRequestURLBuilder constructs an OAuth 2.0 authorization URL
 * using a fluent, customizable interface. It supports optional parameters like
 * PKCE (code challenge), default OpenID scopes, state, nonce, and additional custom fields.
 * <p>
 * Example usage:
 * <pre>
 *     String authUrl = new OAuthRequestURLBuilder("https://accounts.google.com/o/oauth2/auth")
 *         .withClientId("my-client-id")
 *         .withRedirectURI("http://localhost:8080/callback")
 *         .withCodeChallenge("my-generated-challenge")
 *         .withPrompt("consent")
 *         .build();
 * </pre>
 */
public class OAuthRequestURLBuilder {

    private final String baseURL;
    private final Map<String, String> params = new LinkedHashMap<>();

    private boolean includeDefaultScope = true;
    private boolean includeState = true;
    private boolean includeNonce = true;
    private boolean includePKCE = false;
    private String codeChallenge;

    /**
     * Constructs a new OAuthRequestURLBuilder with the base authorization URL.
     *
     * @param baseURL The base URL of the OAuth 2.0 authorization endpoint.
     */
    public OAuthRequestURLBuilder(@NotNull String baseURL) {
        this.baseURL = baseURL;
    }

    /**
     * Sets the OAuth client ID.
     *
     * @param clientId The client identifier.
     * @return The current builder instance.
     */
    public OAuthRequestURLBuilder withClientId(@NotNull String clientId) {
        params.put("client_id", clientId);
        return this;
    }

    /**
     * Sets the redirect URI used in the OAuth flow.
     *
     * @param redirectURI The redirect/callback URI.
     * @return The current builder instance.
     */
    public OAuthRequestURLBuilder withRedirectURI(@NotNull String redirectURI) {
        params.put("redirect_uri", redirectURI);
        return this;
    }

    /**
     * Sets the requested scope and disables the default OpenID scope.
     *
     * @param scope Space-separated list of scopes.
     * @return The current builder instance.
     */
    public OAuthRequestURLBuilder withScope(@NotNull String scope) {
        params.put("scope", scope);
        includeDefaultScope = false;
        return this;
    }

    /**
     * Adds PKCE support using the specified code challenge.
     *
     * @param codeChallenge The code challenge (usually SHA256 hash of a verifier).
     * @return The current builder instance.
     */
    public OAuthRequestURLBuilder withCodeChallenge(@NotNull String codeChallenge) {
        this.includePKCE = true;
        this.codeChallenge = codeChallenge;
        return this;
    }

    /**
     * Adds a custom parameter to the query string.
     *
     * @param key   Parameter name.
     * @param value Parameter value.
     * @return The current builder instance.
     */
    public OAuthRequestURLBuilder withParam(@NotNull String key, @NotNull String value) {
        params.put(key, value);
        return this;
    }

    /**
     * Adds multiple custom parameters to the query string.
     *
     * @param customParams Map of parameter key-value pairs.
     * @return The current builder instance.
     */
    public OAuthRequestURLBuilder withParams(@NotNull Map<String, String> customParams) {
        params.putAll(customParams);
        return this;
    }

    /**
     * Shortcut to add a "prompt" parameter.
     *
     * @param prompt The prompt value (e.g., "consent", "select_account").
     * @return The current builder instance.
     */
    public OAuthRequestURLBuilder withPrompt(@NotNull String prompt) {
        return withParam("prompt", prompt);
    }

    /**
     * Disables automatic state generation.
     *
     * @return The current builder instance.
     */
    public OAuthRequestURLBuilder disableState() {
        this.includeState = false;
        return this;
    }

    /**
     * Disables automatic nonce generation.
     *
     * @return The current builder instance.
     */
    public OAuthRequestURLBuilder disableNonce() {
        this.includeNonce = false;
        return this;
    }

    /**
     * Disables automatic insertion of the default OpenID scope.
     *
     * @return The current builder instance.
     */
    public OAuthRequestURLBuilder disableDefaultScope() {
        this.includeDefaultScope = false;
        return this;
    }

    /**
     * Builds and returns the fully encoded OAuth 2.0 authorization URL.
     *
     * @return The complete authorization URL as a string.
     * @throws IOException if required parameters are missing.
     */
    @NotNull
    public String build() throws IOException {
        if (!params.containsKey("client_id")) {
            throw new IOException("Missing client_id");
        }
        if (!params.containsKey("redirect_uri")) {
            throw new IOException("Missing redirect_uri");
        }

        params.putIfAbsent("response_type", "code");

        if (includePKCE) {
            if (codeChallenge == null) {
                throw new IOException("Missing code challenge");
            }
            params.put("code_challenge", codeChallenge);
            params.put("code_challenge_method", "S256");
        }

        if (includeState) {
            params.putIfAbsent("state", UUID.randomUUID().toString());
        }

        if (includeNonce) {
            params.putIfAbsent("nonce", UUID.randomUUID().toString());
        }

        if (includeDefaultScope && !params.containsKey("scope")) {
            params.put("scope", "openid email profile");
        }

        StringJoiner encodedParams = new StringJoiner("&");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            encodedParams.add(
                URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" +
                    URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)
            );
        }

        return baseURL + "?" + encodedParams;
    }
}
