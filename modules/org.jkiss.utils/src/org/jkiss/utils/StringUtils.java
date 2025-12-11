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

public final class StringUtils {

    private StringUtils() {}

    /**
     * Returns the string wrapped in single quotes if not already.
     *
     * <pre>
     * {@code
     * String str = "value";
     * // Output: "'value'"
     * }
     * </pre>
     */
    @Nullable
    public static String quoteStringIfNotQuoted(@Nullable String value) {
        if (CommonUtils.isEmpty(value)) {
            return value;
        }
        boolean isQuoted = value.startsWith("'") && value.endsWith("'");
        return isQuoted ? value : "'" + value + "'";
    }

    // Originally taken from https://stackoverflow.com/questions/5662094/can-i-wrap-text-to-a-given-width-with-guava
    public static String wrap(String str, int wrapLength) {
        int offset = 0;
        StringBuilder resultBuilder = new StringBuilder();

        while ((str.length() - offset) > wrapLength) {
            if (str.charAt(offset) == ' ') {
                offset++;
                continue;
            }

            int spaceToWrapAt = str.lastIndexOf(' ', wrapLength + offset);
            // if the next string with length maxLength doesn't contain ' '
            if (spaceToWrapAt < offset) {
                spaceToWrapAt = str.indexOf(' ', wrapLength + offset);
                // if no more ' '
                if (spaceToWrapAt < 0) {
                    break;
                }
            }

            resultBuilder.append(str, offset, spaceToWrapAt);
            resultBuilder.append("\n");
            offset = spaceToWrapAt + 1;
        }

        resultBuilder.append(str.substring(offset));
        return resultBuilder.toString();
    }

    public static String truncateToSpace(String str, int wrapLength) {
        int spaceToWrapAt = str.indexOf(' ', wrapLength);
        if (spaceToWrapAt < 0) {
            return str;
        }

        return str.substring(0, spaceToWrapAt) + "...";
    }

    @NotNull
    public static String truncateText(@NotNull String str, int maxLength) {
        if (str.length() > maxLength) {
            return str.substring(0, maxLength) + "...";
        }
        return str;
    }

    /**
     * Converts an {@code under_score} string to {@code camelCase}.
     *
     * {@snippet id="underScoreToCamelCaseExample" lang="java" :
     * String value = underScoreToCamelCase("some_field");
     * // value == "someField"
     * }
     */
    @Nullable
    public static String underScoreToCamelCase(@Nullable String str) {
        if (CommonUtils.isEmpty(str)) {
            return str;
        }

        StringBuilder result = new StringBuilder(str.length());
        boolean toUpper = false;

        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);

            if (ch == '_') {
                toUpper = true;
            } else {
                if (toUpper) {
                    result.append(Character.toUpperCase(ch));
                    toUpper = false;
                } else {
                    result.append(Character.toLowerCase(ch));
                }
            }
        }

        return result.toString();
    }

    public static String firstNonEmpty(String a, String b) {
        return CommonUtils.isEmpty(a) ? b : a;
    }
}
