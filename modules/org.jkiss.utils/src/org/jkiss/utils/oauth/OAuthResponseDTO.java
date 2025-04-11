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

import java.util.Objects;

public final class OAuthResponseDTO {
    private final String token_type;
    private final long expires_in;
    private final String access_token;
    private final String scope;
    private final String id_token;

    public OAuthResponseDTO(
        String token_type,
        long expires_in,
        String access_token,
        String scope,
        String id_token
    ) {
        this.token_type = token_type;
        this.expires_in = expires_in;
        this.access_token = access_token;
        this.scope = scope;
        this.id_token = id_token;
    }

    public String token_type() {
        return token_type;
    }

    public long expires_in() {
        return expires_in;
    }

    public String access_token() {
        return access_token;
    }

    public String scope() {
        return scope;
    }

    public String id_token() {
        return id_token;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (OAuthResponseDTO) obj;
        return Objects.equals(this.token_type, that.token_type) &&
            this.expires_in == that.expires_in &&
            Objects.equals(this.access_token, that.access_token) &&
            Objects.equals(this.scope, that.scope) &&
            Objects.equals(this.id_token, that.id_token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token_type, expires_in, access_token, scope, id_token);
    }

    @Override
    public String toString() {
        return "OAuthResponseDTO[" +
            "token_type=" + token_type + ", " +
            "expires_in=" + expires_in + ", " +
            "access_token=" + access_token + ", " +
            "scope=" + scope + ", " +
            "id_token=" + id_token + ']';
    }

}
