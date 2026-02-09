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
package org.jkiss.utils.time;

import org.jkiss.code.NotNull;

import java.sql.Timestamp;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Formatter adapted to support nanoseconds from java.sql.Timestamp.
 */
public class ExtendedDateFormat extends SimpleDateFormat {

    private static final String NINE_ZEROES = "000000000";
    private static final int MAX_NANO_LENGTH = 8;

    private int nanoStart = -1, nanoLength;
    private boolean nanoOptional;
    private String nanoPrefix, nanoPostfix;

    public ExtendedDateFormat(@NotNull String pattern) {
        this(pattern, Locale.getDefault());
    }

    public ExtendedDateFormat(@NotNull String pattern, @NotNull Locale locale) {
        super(stripNanos(pattern), locale);

        int quoteCount = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                quoteCount++;
                for (int k = i + 1; k < pattern.length(); k++) {
                    if (pattern.charAt(k) == '\'') {
                        if (k != i + 1) {
                            quoteCount++;
                        }
                        i = k;
                        break;
                    }
                }
            } else if (c == '[') {
                nanoStart = i;
                nanoOptional = true;
                for (int k = i + 1; k < pattern.length(); k++) {
                    if (pattern.charAt(k) == 'f' || pattern.charAt(k) == 'S') {
                        nanoLength++;
                        if (nanoPrefix == null) {
                            nanoPrefix = pattern.substring(i + 1, k);
                        }
                    }
                    if (pattern.charAt(k) == ']') {
                        if (nanoPrefix == null){
                            break;
                        }
                        nanoPostfix = pattern.substring(i + 1 + nanoPrefix.length() + nanoLength, k);
                        i = k + 1;
                        break;
                    }
                }
            } else if (c == 'f' || c == 'S') {
                nanoStart = i - quoteCount;
                nanoOptional = false;
                for (int k = i + 1; k < pattern.length(); k++) {
                    if (pattern.charAt(k) != 'f' && pattern.charAt(k) != 'S') {
                        break;
                    }
                    nanoLength++;
                }
                nanoLength++;
                i = i + nanoLength;
            }
        }
    }

    @Override
    public StringBuffer format(@NotNull Date date, @NotNull StringBuffer toAppendTo, @NotNull FieldPosition pos) {
        StringBuffer result = super.format(date, toAppendTo, pos);
        if (nanoStart >= 0) {
            long nanos;
            if (date instanceof Timestamp) {
                nanos = ((Timestamp) date).getNanos();
            } else {
                // Extract milliseconds from Date and convert to nanoseconds
                nanos = (date.getTime() % 1000) * 1_000_000;
                if (nanos < 0) {
                    nanos += 1_000_000_000; // Handle negative milliseconds
                }
            }
            if (!nanoOptional || nanos > 0) {
                StringBuilder nanosRes = new StringBuilder(nanoLength);
                // Append nanos value in the end
                if (nanoPrefix != null) {
                    nanosRes.append(nanoPrefix);
                }
                String nanoStr = String.valueOf(nanos);

                // nanoStr must be a string of exactly 9 chars in length. Pad with leading "0" if not
                int nbZeroesToPad = 9 - nanoStr.length();
                if (nbZeroesToPad > 0) {
                    nanoStr = NINE_ZEROES.substring(0, nbZeroesToPad) + nanoStr;
                }

                if (nanoLength < nanoStr.length()) {
                    // Truncate nanos string to fit in the pattern
                    nanoStr = nanoStr.substring(0, nanoLength);
                } else {
                    // Pad with 0s
                    int padLength = nanoLength - nanoStr.length();
                    if (padLength > 0) {
                        nanosRes.append("0".repeat(padLength));
                    }
                }
                nanosRes.append(nanoStr);
                if (nanoPostfix != null) {
                    nanosRes.append(nanoPostfix);
                }
                result.insert(nanoStart, nanosRes);
            }
        }
        return result;
    }

    @Override
    public Date parse(@NotNull String text, @NotNull ParsePosition pos) {
        Date date = super.parse(text, pos);
        if (date == null) {
            return null;
        }
        int index = pos.getIndex();
        if (index < text.length() && nanoStart >= 0) {
            long nanos = 0;
            if (nanoPrefix != null) {
                index += nanoPrefix.length();
            }
            for (int i = 0; i < nanoLength; i++) {
                int digitPos = index + i;
                if (digitPos == text.length()) {
                    break;
                }
                char c = text.charAt(digitPos);
                if (!Character.isDigit(c)) {
                    pos.setErrorIndex(index);
                    pos.setIndex(index);
                    //throw new ParseException("Invalid nanosecond character at pos " + digitPos + ": " + c, index);
                    return null;
                }
                long digit = ((int)c - (int)'0');
                for (int k = MAX_NANO_LENGTH - i; k > 0; k--) {
                    digit *= 10;
                }
                nanos += digit;
            }
            if (nanos > 0) {
                Timestamp ts = new Timestamp(date.getTime());
                ts.setNanos((int)nanos);
                return ts;
            }
        }
        return date;
    }

    @NotNull
    private static String stripNanos(@NotNull String pattern) {
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                for (int k = i + 1; k < pattern.length(); k++) {
                    if (pattern.charAt(k) == '\'') {
                        i = k;
                        break;
                    }
                }
            } else if (c == '[') {
                for (int k = i + 1; k < pattern.length(); k++) {
                    if (pattern.charAt(k) == ']') {
                        return pattern.substring(0, i) + pattern.substring(k + 1);
                    }
                }
            } else if (c == 'f' || c == 'S') {
                for (int k = i + 1; k < pattern.length(); k++) {
                    if (pattern.charAt(k) != 'f' && pattern.charAt(k) != 'S') {
                        return pattern.substring(0, i) + pattern.substring(k);
                    }
                }
                return pattern.substring(0, i);
            }
        }
        return pattern;
    }
}
