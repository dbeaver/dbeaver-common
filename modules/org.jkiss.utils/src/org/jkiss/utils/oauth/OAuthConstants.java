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

public class OAuthConstants {
    public static final int AUTH_DEFAULT_SSO_TIMEOUT = 120;
    public static final int SSO_CALLBACK_PORT_DEFAULT = 8000;

    public static final String AUTH_SSO_CALLBACK_TEMPLATE = "http://localhost:%s%s";
    public static final String DEFAULT_CALLBACK_ENDPOINT = "/callback";

    public static final String AUTH_PROP_CLIENT_ID = "client_id";
    public static final String AUTH_PROP_CLIENT_SECRET = "client_secret";
    public static final String AUTH_PROP_TOKEN = "token";

    public static final String RESULT_PROP_TOKEN_ID = "id_token";
}