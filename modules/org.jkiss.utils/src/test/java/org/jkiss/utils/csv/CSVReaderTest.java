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
package org.jkiss.utils.csv;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CSVReaderTest {

    private static final String DEFAULT_SEPARATOR = ",";
    private static final String DEFAULT_QUOTE = "\"";
    private static final String DEFAULT_ESCAPE = "\\";

    @ParameterizedTest
    @ValueSource(strings = {DEFAULT_SEPARATOR, ";"})
    void testReadAllSingleLine(@NotNull String separator) throws Exception {
        // given
        String input = "a,b,c";
        List<String[]> expected = rows(
            row("a", "b", "c")
        );

        // then
        assertReadAll(
            expected,
            input,
            separator,
            null,
            null
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {DEFAULT_SEPARATOR, ";"})
    void testReadAllMultiLine(@NotNull String separator) throws Exception {
        assertReadAll(
            rows(
                row("a", "b", "c"),
                row("1", "2", "3"),
                row("x", "y", "z")
            ),
            "a,b,c\n1,2,3\nx,y,z",
            separator,
            null,
            null
        );
    }

    @ParameterizedTest
    @MethodSource("provideSeparators")
    void testReadAllMultilineInsideQuotes(@NotNull String separator, @NotNull String quote, @NotNull String escape) throws Exception {
        assertReadAll(
            rows(
                row("a", "hello\nworld", "c"),
                row("1", "2", "3")
            ),
            "a,\"hello\nworld\",c\n1,2,3",
            separator,
            quote,
            escape
        );
    }

    @ParameterizedTest
    @MethodSource("provideSeparators")
    void testReadSeparatorInsideQuotes(@NotNull String separator, @NotNull String quote, @NotNull String escape) throws Exception {
        assertReadAll(
            rows(
                row("a", "hello" + separator + "world", "c"),
                row("1", "2", "3")
            ),
            "a,\"hello" + separator + "world\",c\n1,2,3",
            separator,
            quote,
            escape
        );
    }

    @ParameterizedTest
    @MethodSource("provideSeparators")
    void testReadAllEscapedQuotes(@NotNull String separator, @NotNull String quote, @NotNull String escape) throws Exception {
        assertReadAll(
            rows(
                row("a", "hello " + quote + "world" + quote, "c")
            ),
            "a,\"hello " + "\\" + "\"world" + "\\" + "\"" + "\"" + ",c",
            separator,
            quote,
            escape
        );
    }

    @ParameterizedTest
    @MethodSource("provideSeparators")
    void testReadAllEscapedEscape(@NotNull String separator, @NotNull String quote, @NotNull String escape) throws Exception {
        assertReadAll(
            rows(
                row("a", "b" + escape + "c", "d")
            ),
            "a,b\\" + escape + "c,d",
            separator,
            quote,
            escape
        );
    }

    private void assertReadAll(
        @NotNull List<String[]> expected,
        @NotNull String csvTemplate,
        @Nullable String separator,
        @Nullable String quoteChar,
        @Nullable String escape
    ) throws Exception {

        var csv = replaceInCSVToCustom(csvTemplate, separator, quoteChar, escape);
        try (CSVReader reader = createReader(csv, separator, quoteChar, escape)) {
            List<String[]> actual = reader.readAll();

            assertEquals(
                expected.size(),
                actual.size(),
                "Differed rows length\nExpected:" + expected.stream().map(Arrays::toString).collect(Collectors.joining())
                    + "\nActual  :" + actual.stream().map(Arrays::toString).collect(Collectors.joining())
            );
            for (int i = 0; i < expected.size(); i++) {
                assertArrayEquals(
                    expected.get(i), actual.get(i), "CSV: " + csv + "\nExpected: "
                        + Arrays.toString(expected.get(i)) + "\nActual  : " + Arrays.toString(actual.get(i))
                );
            }
        }
    }

    private String replaceInCSVToCustom(
        @NotNull String csvTemplate,
        @Nullable String separator,
        @Nullable String quoteChar,
        @Nullable String escape
    ) {
        if (separator != null) {
            csvTemplate = csvTemplate.replace(DEFAULT_SEPARATOR, separator);
        }
        if (quoteChar != null) {
            csvTemplate = csvTemplate.replace(DEFAULT_QUOTE, quoteChar);
        }
        if (escape != null) {
            csvTemplate = csvTemplate.replace(DEFAULT_ESCAPE, escape);
        }
        return csvTemplate;
    }

    @NotNull
    private CSVReader createReader(
        @NotNull String csv,
        @Nullable String separator,
        @Nullable String quoteChar,
        @Nullable String escape
    ) {
        return new CSVReader(
            new StringReader(csv),
            Objects.requireNonNullElse(separator, DEFAULT_SEPARATOR).charAt(0),
            Objects.requireNonNullElse(quoteChar, DEFAULT_QUOTE).charAt(0),
            Objects.requireNonNullElse(escape, DEFAULT_ESCAPE).charAt(0),
            0,
            false
        );
    }

    @NotNull
    private List<String[]> rows(String[]... rows) {
        return List.of(rows);
    }

    @NotNull
    private String[] row(String... values) {
        return values;
    }

    private static List<Arguments> provideSeparators() {
        return List.of(
            //defaults
            Arguments.of(DEFAULT_SEPARATOR, DEFAULT_QUOTE, DEFAULT_ESCAPE),
            // alternative one char
            Arguments.of(";", "'", "~")
        );
    }

}