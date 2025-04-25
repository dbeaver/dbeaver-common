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

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.util.List;
import java.util.Map;

public class WSClientUtils {
    @Nullable
    public static List<String> getHeaders(@Nullable Map<String, List<String>> allHeaders, @NotNull String headerName) {
        if (allHeaders == null) {
            return null;
        }
        List<String> headerValues = allHeaders.get(headerName);
        if (headerValues == null) {
            headerValues = allHeaders.get(headerName.toLowerCase());
        }
        return headerValues;
    }
}
