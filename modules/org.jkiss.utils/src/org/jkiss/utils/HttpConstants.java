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
package org.jkiss.utils;

public class HttpConstants {
    public static final String HEADER_USER_AGENT = "User-Agent";

    public static final String HEADER_X_REFERRER = "X-Referrer";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_AUTHORIZATION = "Authorization";

    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_APP_FORM = "application/x-www-form-urlencoded";

    public static final int CODE_BAD_REQUEST = 400;
    public static final int CODE_UNAUTHORIZED = 401;
    public static final int CODE_FORBIDDEN = 403;
    public static final int CODE_NOT_FOUND = 404;
    public static final int CODE_PAYLOAD_TOO_LARGE = 413;
    public static final int CODE_TOO_MANY_REQUESTS = 429;
    public static final int CODE_INTERNAL_SERVER_ERROR = 500;

}
