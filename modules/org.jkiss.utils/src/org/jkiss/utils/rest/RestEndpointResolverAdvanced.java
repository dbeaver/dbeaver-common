/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
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

package org.jkiss.utils.rest;

import org.jkiss.code.NotNull;
import org.jkiss.utils.CommonUtils;

/**
 * Advanced rest endpoint resolver.
 * Gets first word from the method name and puts it to the end of the result.
 */
public class RestEndpointResolverAdvanced implements RestEndpointResolver {

    @NotNull
    @Override
    public String generateEndpointName(String methodName) {
        String[] parts = methodName.split("(?=\\p{Lu})");
        String[] result = new String[parts.length];
        result[result.length - 1] = parts[0].toLowerCase();
        for (int i = 1; i < parts.length; i++) {
            result[i - 1] = parts[i].toLowerCase();
        }
        return CommonUtils.joinStrings("/", result);
    }
}
