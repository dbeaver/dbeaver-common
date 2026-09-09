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
package org.jkiss.utils;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpUtils {

    @NotNull
    public static Map<String, String> parseQuery(@Nullable String query) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) {
            return parameters;
        }
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0) {
                parameters.put(
                    URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8)
                );
            } else {
                parameters.put(
                    URLDecoder.decode(pair, StandardCharsets.UTF_8),
                    "");
            }
        }
        return parameters;
    }

}
