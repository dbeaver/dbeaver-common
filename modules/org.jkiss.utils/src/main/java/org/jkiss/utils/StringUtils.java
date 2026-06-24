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

public final class StringUtils {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

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
     * {@snippet id = "underScoreToCamelCaseExample" lang = "java":
     * String value = underscoreToCamelCase("some_field");
     * // value == "someField"
     *}
     */
    @Nullable
    public static String underscoreToCamelCase(@Nullable String str) {
        if (CommonUtils.isEmpty(str)) {
            return str;
        }

        StringBuilder result = new StringBuilder(str.length());
        boolean toUpper = false;

        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);

            if (ch == '_') {
                toUpper = true;
            } else if (toUpper) {
                result.append(Character.toUpperCase(ch));
                toUpper = false;
            } else {
                result.append(Character.toLowerCase(ch));
            }
        }

        return result.toString();
    }

    @Nullable
    public static String firstNonEmpty(@Nullable String a, @Nullable String b) {
        return CommonUtils.isEmpty(a) ? b : a;
    }

    // https://stackoverflow.com/a/25379180
    public static boolean containsIgnoreCase(@NotNull String src, @NotNull String what) {
        final int length = what.length();
        if (length == 0)
            return true; // Empty string is contained

        final char firstLo = Character.toLowerCase(what.charAt(0));
        final char firstUp = Character.toUpperCase(what.charAt(0));

        for (int i = src.length() - length; i >= 0; i--) {
            // Quick check before calling the more expensive regionMatches() method:
            final char ch = src.charAt(i);
            if (ch != firstLo && ch != firstUp)
                continue;

            if (src.regionMatches(true, i, what, 0, length))
                return true;
        }

        return false;
    }

    /**
     * Escapes non-ASCII characters and the backslash as {@code \\uXXXX}.
     */
    @NotNull
    public static String escapeUnicode(@NotNull String str) {
        int length = str.length();
        int start = -1;
        for (int i = 0; i < length; i++) {
            char ch = str.charAt(i);
            if (ch < 0x20 || ch > 0x7E || ch == '\\') {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return str;
        }

        StringBuilder sb = new StringBuilder(length + 16);
        sb.append(str, 0, start);
        for (int i = start; i < length; i++) {
            char ch = str.charAt(i);
            if (ch >= 0x20 && ch <= 0x7E && ch != '\\') {
                sb.append(ch);
            } else if (ch == '\\') {
                sb.append("\\\\");
            } else {
                sb.append('\\').append('u')
                    .append(HEX_DIGITS[(ch >> 12) & 0xF])
                    .append(HEX_DIGITS[(ch >> 8) & 0xF])
                    .append(HEX_DIGITS[(ch >> 4) & 0xF])
                    .append(HEX_DIGITS[ch & 0xF]);
            }
        }

        return sb.toString();
    }

    /**
     * Reverses {@link #escapeUnicode(String)}.
     */
    @NotNull
    public static String unescapeUnicode(@NotNull String str) {
        int start = str.indexOf('\\');
        if (start < 0) {
            return str;
        }

        int length = str.length();
        StringBuilder sb = new StringBuilder(length);
        sb.append(str, 0, start);
        for (int i = start; i < length; ) {
            char ch = str.charAt(i);

            if (ch != '\\') {
                sb.append(ch);
                i++;
                continue;
            }

            if (i + 1 >= length) {
                throw new IllegalArgumentException("Trailing '\\'");
            }

            char next = str.charAt(i + 1);

            if (next == '\\') {
                sb.append('\\');
                i += 2;
            } else if (next == 'u') {
                if (i + 6 > length) {
                    throw new IllegalArgumentException("Incomplete unicode escape");
                }

                sb.append((char) Integer.parseInt(str, i + 2, i + 6, 16));
                i += 6;
            } else {
                throw new IllegalArgumentException("Unknown escape: \\" + next);
            }
        }

        return sb.toString();
    }

}
