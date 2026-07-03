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
package com.dbeaver.rest.client;

import org.jkiss.utils.HttpConstants;

public enum MediaType {
    JSON(HttpConstants.CONTENT_TYPE_JSON),
    TEXT(HttpConstants.CONTENT_TYPE_TEXT_PLAIN),
    OCTET_STREAM(HttpConstants.CONTENT_TYPE_OCTET_STREAM),
    XML(HttpConstants.CONTENT_TYPE_TEXT_XML);

    private final String value;

    MediaType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
