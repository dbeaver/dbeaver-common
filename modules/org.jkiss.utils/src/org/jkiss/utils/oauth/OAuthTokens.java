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

import org.jkiss.code.Nullable;

import java.util.Objects;

public final class OAuthTokens {
    @Nullable
    private final String accessToken;
    @Nullable
    private final String refreshToken;

    public OAuthTokens(@Nullable String accessToken, @Nullable String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    @Nullable
    public String accessToken() {
        return accessToken;
    }

    @Nullable
    public String refreshToken() {
        return refreshToken;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (OAuthTokens) obj;
        return Objects.equals(this.accessToken, that.accessToken) &&
            Objects.equals(this.refreshToken, that.refreshToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessToken, refreshToken);
    }

    @Override
    public String toString() {
        return "OAuthTokens[" +
            "accessToken=" + accessToken + ", " +
            "refreshToken=" + refreshToken + ']';
    }

}
