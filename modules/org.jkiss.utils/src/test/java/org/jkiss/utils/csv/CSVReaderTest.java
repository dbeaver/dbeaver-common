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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CSVReaderTest {

    private static final String DEFAULT_SEPARATOR = ",";
    private static final String DEFAULT_QUOTE = "\"";
    private static final String DEFAULT_ESCAPE = "\\";

    private static final String ALTERNATIVE_SEPARATOR = ".";
    private static final String ALTERNATIVE_QUOTE = "'";
    private static final String ALTERNATIVE_ESCAPE = "~";

    @Nested
    class ReaderConstructorTest {

        @Test
        public void testNullCharInQuoteAndEscapeDoesNotThrow() {
            String csv = "test";
            assertDoesNotThrow(() -> new CSVReader(new StringReader(csv), DEFAULT_SEPARATOR, CSVParser.NULL_CHARACTER, DEFAULT_ESCAPE));
            assertDoesNotThrow(() -> new CSVReader(new StringReader(csv), DEFAULT_SEPARATOR, DEFAULT_QUOTE, CSVParser.NULL_CHARACTER));
        }

        @Test
        void testNullCharInSeparatorThrows() {
            String csv = "test";
            assertThrows(
                UnsupportedOperationException.class,
                () -> new CSVReader(new StringReader(csv), CSVParser.NULL_CHARACTER, DEFAULT_QUOTE, DEFAULT_ESCAPE)
            );
        }

        @Test
        public void testSameMainSeparatorsThrows() {
            String csv = "test";
            assertThrows(
                UnsupportedOperationException.class,
                () -> new CSVReader(new StringReader(csv), DEFAULT_SEPARATOR, DEFAULT_SEPARATOR, DEFAULT_ESCAPE)
            );
            assertThrows(
                UnsupportedOperationException.class,
                () -> new CSVReader(new StringReader(csv), DEFAULT_SEPARATOR, DEFAULT_ESCAPE, DEFAULT_ESCAPE)
            );
            assertThrows(
                UnsupportedOperationException.class,
                () -> new CSVReader(new StringReader(csv), DEFAULT_SEPARATOR, DEFAULT_ESCAPE, DEFAULT_SEPARATOR)
            );
        }

        @Test
        public void testUnfinishedQuotationShouldThrow() {
            String csv = "a," + DEFAULT_QUOTE + "b";
            CSVReader reader = new CSVReader(new StringReader(csv), DEFAULT_SEPARATOR, DEFAULT_QUOTE, DEFAULT_ESCAPE);
            assertThrows(IOException.class, reader::readAll);
        }
    }


    @ParameterizedTest
    @MethodSource("provideSeparators")
    void testReadAllBasicCase(@NotNull String separator) throws Exception {
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
    @MethodSource("provideSeparators")
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
    void testReadAllQuotesInMiddleOfLine(@NotNull String separator, @NotNull String quote, @NotNull String escape) throws Exception {
        assertReadAll(
            rows(
                row("a", "bc\"d\"ef", "g")
            ),
            "a,bc\"d\"ef,g",
            separator,
            quote,
            escape
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
        // see: https://www.rfc-editor.org/rfc/rfc4180.txt
    void testQuotesInsideQuotesAreTreatedEscaped(@NotNull String separator, @NotNull String quote, @NotNull String escape)
    throws Exception {
        assertReadAll(
            rows(
                row("\"\"a", "hello\"\"world", "\"\"c", "\"\"d\"\"")
            ),
            "\"a, hello\"world,c\",\"d\"",
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
                row("hello " + quote + "world" + quote),
                row(escape + quote)
            ),
            "\"hello " + "\\" + "\"world" + "\\" + "\"" + "\"" + "\n\\\"",
            separator,
            quote,
            escape
        );
    }

    @ParameterizedTest
    @MethodSource("provideSeparators")
    void testReadAllEscapedSimpleCharIsAppendedWithEscape(@NotNull String separator, @NotNull String quote, @NotNull String escape)
    throws Exception {
        assertReadAll(
            rows(
                row("a", escape + "b", escape + "c")
            ),
            "a,\\b,\\c",
            separator,
            quote,
            escape
        );
    }

    @ParameterizedTest
    @MethodSource("provideSeparators")
    void testReadAllEscapeInTheEndOfTheLine(@NotNull String separator, @NotNull String quote, @NotNull String escape)
    throws Exception {
        assertReadAll(
            rows(
                row("a", "b", "c" + escape),
                row(escape + "1", "2", "3")
            ),
            "a,b,c\\\n\\1,2,3",
            separator,
            quote,
            escape
        );
    }

    @ParameterizedTest
    @MethodSource("provideSeparators")
    void testEscapeInQuotesBeforeUnescapableCharAppended(@NotNull String separator, @NotNull String quote, @NotNull String escape)
    throws Exception {
        assertReadAll(
            rows(
                row("a", "\b", "c", "d")
            ),
            "a,\"\b" + escape + "\",c,d",
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

    @ParameterizedTest
    @MethodSource("provideSeparators")
    void testReadAllEscapedEscapeBeforeQuotes(@NotNull String separator, @NotNull String quote, @NotNull String escape) throws Exception {
        assertReadAll(
            rows(
                row("a", "b" + escape, "c", "d")
            ),
            "a,b" + escape.repeat(2) + "\",c,d",
            separator,
            quote,
            escape
        );
    }

    @ParameterizedTest
    @MethodSource("provideSeparators")
    void testReadAllIgnoresLeadingWhitespaceBeforeQuote(
        @NotNull String separator,
        @NotNull String quote,
        @NotNull String escape
    ) throws Exception {
        assertReadAll(
            rows(
                row("a", "b", "c")
            ),
            "a,   \"b\",c",
            separator,
            quote,
            escape,
            CSVReader.DEFAULT_SKIP_LINES,
            false,
            true
        );
    }

    @ParameterizedTest
    @MethodSource("provideSeparators")
    void testReadAllDoesNotIgnoreLeadingWhitespaceBeforeQuote(
        @NotNull String separator,
        @NotNull String quote,
        @NotNull String escape
    ) throws Exception {
        assertReadAll(
            rows(
                row("a", "   " + quote + "b" + quote, "c")
            ),
            "a,   \"b,c",
            separator,
            quote,
            escape,
            CSVReader.DEFAULT_SKIP_LINES,
            false,
            true
        );
    }
    // todo add empty stuff tests

    private void assertReadAll(
        @NotNull List<String[]> expected,
        @NotNull String csvTemplate,
        @Nullable String separator,
        @Nullable String quoteChar,
        @Nullable String escape
    ) throws Exception {
        assertReadAll(
            expected,
            csvTemplate,
            separator,
            quoteChar,
            escape,
            CSVReader.DEFAULT_SKIP_LINES,
            CSVParser.DEFAULT_STRICT_QUOTES,
            CSVParser.DEFAULT_IGNORE_LEADING_WHITESPACE
        );
    }

    private void assertReadAll(
        @NotNull List<String[]> expected,
        @NotNull String csvTemplate,
        @Nullable String separator,
        @Nullable String quoteChar,
        @Nullable String escape,
        int line,
        boolean strictQuotes,
        boolean ignoreLeadingWhiteSpace
    ) throws Exception {

        var csv = replaceInCSVToCustom(csvTemplate, separator, quoteChar, escape);
        try (CSVReader reader = createReader(csv, separator, quoteChar, escape, line, strictQuotes, ignoreLeadingWhiteSpace)) {
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
        @Nullable CharSequence separator,
        @Nullable CharSequence quoteChar,
        @Nullable CharSequence escape
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
        @Nullable CharSequence separator,
        @Nullable CharSequence quoteChar,
        @Nullable CharSequence escape,
        int line,
        boolean strictQuotes,
        boolean ignoreLeadingWhiteSpace
    ) {
        return new CSVReader(
            new StringReader(csv),
            Objects.requireNonNullElse(separator, DEFAULT_SEPARATOR),
            Objects.requireNonNullElse(quoteChar, DEFAULT_QUOTE),
            Objects.requireNonNullElse(escape, DEFAULT_ESCAPE),
            line,
            strictQuotes,
            ignoreLeadingWhiteSpace
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
            Arguments.of(ALTERNATIVE_SEPARATOR, ALTERNATIVE_QUOTE, ALTERNATIVE_ESCAPE),
            // default + alt 2 chars
            Arguments.of(DEFAULT_SEPARATOR + ALTERNATIVE_SEPARATOR, DEFAULT_QUOTE + ALTERNATIVE_QUOTE, DEFAULT_ESCAPE + ALTERNATIVE_ESCAPE),
            // all special chars but starts same all cases
            Arguments.of(DEFAULT_SEPARATOR, DEFAULT_SEPARATOR + DEFAULT_QUOTE, DEFAULT_SEPARATOR + DEFAULT_ESCAPE),
            Arguments.of(DEFAULT_QUOTE + DEFAULT_SEPARATOR, DEFAULT_QUOTE, DEFAULT_QUOTE + DEFAULT_ESCAPE),
            Arguments.of(DEFAULT_ESCAPE + DEFAULT_QUOTE + DEFAULT_SEPARATOR, DEFAULT_ESCAPE + DEFAULT_QUOTE, DEFAULT_ESCAPE)
        );
    }

}