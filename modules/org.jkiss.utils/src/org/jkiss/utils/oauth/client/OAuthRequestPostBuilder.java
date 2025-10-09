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

import org.jkiss.utils.CommonUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;

public class OAuthRequestPostBuilder {
    private URI tokenUri;
    private String clientId;
    private String grantType;
    private String clientSecret;

    public OAuthRequestPostBuilder(String authUrl) {
        if (CommonUtils.isNotEmpty(authUrl)){
            this.tokenUri = URI.create(authUrl);
        }
    }

    public OAuthRequestPostBuilder withClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public OAuthRequestPostBuilder withClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
        return this;
    }

    /**
     * Grant type for the request.
     *
     */
    public OAuthRequestPostBuilder withGrantType(String grantType) {
        this.grantType = grantType;
        return this;
    }

    public HttpRequest build() {
        HttpRequest.Builder builder = tokenUri == null ? HttpRequest.newBuilder() : HttpRequest.newBuilder(tokenUri);
        return builder.POST(HttpRequest.BodyPublishers.ofString(
                "&grant_type=" + URLEncoder.encode(grantType, StandardCharsets.UTF_8)
        )).header("Authorization", "Basic " +
                java.util.Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();
    }
}
