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

import java.nio.CharBuffer;
import java.util.Comparator;

/**
 * A comparator for comparing two strings lexicographically, treating them as sequences of alphanumeric characters.
 * <p>
 * This comparator compares strings based on their alphanumeric content. It considers
 * the characters in the strings as a sequence of alphanumeric characters (letters and digits)
 * and compares them lexicographically. The comparison is case-insensitive.
 */
public class AlphanumericComparator implements Comparator<CharSequence> {
    private static final AlphanumericComparator INSTANCE = new AlphanumericComparator();

    private AlphanumericComparator() {
        // prevents instantiation
    }

    @NotNull
    public static AlphanumericComparator getInstance() {
        return INSTANCE;
    }

    @Override
    public int compare(CharSequence o1, CharSequence o2) {
        CharBuffer b1 = CharBuffer.wrap(o1);
        CharBuffer b2 = CharBuffer.wrap(o2);

        while (b1.hasRemaining() && b2.hasRemaining()) {
            adjustBufferWindow(b1);
            adjustBufferWindow(b2);

            int result = compare(b1, b2);
            if (result != 0) {
                return result;
            }

            resetBufferWindow(b1);
            resetBufferWindow(b2);
        }

        return Integer.compare(o1.length(), o2.length());
    }

    private static int compare(@NotNull CharBuffer b1, @NotNull CharBuffer b2) {
        if (isDigit(b1, b1.position()) && isDigit(b2, b2.position())) {
            int diff = Integer.compare(b1.length(), b2.length());
            if (diff != 0) {
                return diff;
            }
            for (int i = 0; i < b1.remaining() && i < b2.remaining(); i++) {
                int result = Character.compare(b1.charAt(i), b2.charAt(i));
                if (result != 0) {
                    return result;
                }
            }
            return 0;
        } else {
            return b1.compareTo(b2);
        }
    }

    private static void resetBufferWindow(@NotNull CharBuffer buffer) {
        buffer.position(buffer.limit());
        buffer.limit(buffer.capacity());
    }

    private static void adjustBufferWindow(@NotNull CharBuffer buffer) {
        var start = buffer.position();
        var end = buffer.position();
        var digit = isDigit(buffer, start);

        while (end < buffer.limit() && digit == isDigit(buffer, end)) {
            end += 1;

            if (digit && start + 1 < buffer.limit() && isZero(buffer, start) && isDigit(buffer, end)) {
                start += 1;
            }
        }

        buffer.position(start).limit(end);
    }

    private static boolean isDigit(@NotNull CharBuffer buffer, int position) {
        char ch = buffer.get(position);
        return ch >= '0' && ch <= '9';
    }

    private static boolean isZero(@NotNull CharBuffer buffer, int position) {
        return buffer.get(position) == '0';
    }
}
