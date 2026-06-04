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
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.ParameterDeclarations;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CSVReaderTest {

    private static final String DEFAULT_SEPARATOR = ",";
    private static final String DEFAULT_QUOTE = "\"";
    private static final String DEFAULT_ESCAPE = "\\";

    private static final String ALTERNATIVE_SEPARATOR = ".";
    private static final String ALTERNATIVE_QUOTE = "'";
    private static final String ALTERNATIVE_ESCAPE = "|";

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
    }

    @ParameterizedTest
    @ArgumentsSource(SeparatorsProvider.class)
    void testBasicCase(@NotNull String separator) throws Exception {
        // given
        String input = "a,b,c";

        // then
        assertReadAll(
            rows(
                row("a", "b", "c")
            ),
            input,
            separator,
            null,
            null
        );
    }

    @ParameterizedTest
    @ArgumentsSource(SeparatorsProvider.class)
    void testMultiLine(@NotNull String separator) throws Exception {
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

    @Nested
    class QuotesTests {

        @ParameterizedTest
        @ArgumentsSource(SeparatorsProvider.class)
        void testQuotesInMiddleOfLine(@NotNull String separator, @NotNull String quote, @NotNull String escape) throws Exception {
            assertReadAll(
                rows(
                    row("1", "23\"4\"56", "7")
                ),
                "1,23\"4\"56,7",
                separator,
                quote,
                escape
            );
        }

        @ParameterizedTest
        @ArgumentsSource(SeparatorsProvider.class)
        void testQuotesInMiddleOfLineWithSeparator(@NotNull String separator, @NotNull String quote, @NotNull String escape)
        throws Exception {
            assertReadAll(
                rows(
                    row("1", "23\"4,5\"6", "7")
                ),
                "1,23\"4,5\"6,7",
                separator,
                quote,
                escape
            );
        }

        @ParameterizedTest
        @ArgumentsSource(SeparatorsProvider.class)
        void testQuotesInMiddleOfLineWithNewLine(@NotNull String separator, @NotNull String quote, @NotNull String escape)
        throws Exception {
            assertReadAll(
                rows(
                    row("1", "23\"4\n5\"6", "7")
                ),
                "1,23\"4\n5\"6,7",
                separator,
                quote,
                escape
            );
        }

        @ParameterizedTest
        @ArgumentsSource(SeparatorsProvider.class)
        void testMultilineInsideQuotes(@NotNull String separator, @NotNull String quote, @NotNull String escape) throws Exception {
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
        @ArgumentsSource(SeparatorsProvider.class)
            // see: https://www.rfc-editor.org/rfc/rfc4180.txt
        void testDoubleQuotesInsideQuotesAreTreatedEscaped(@NotNull String separator, @NotNull String quote, @NotNull String escape)
        throws Exception {

            assertReadAll(
                rows(
                    row("1", "\"", "2")
                ),
                "1,\"\"\"\",2",
                separator,
                quote,
                escape
            );

            assertReadAll(
                rows(
                    row("1", "2\"3", "4")
                ),
                "1,\"2\"\"3\",4",
                separator,
                quote,
                escape
            );

            assertReadAll(
                rows(
                    row("1", "2\"word\"3", "4")
                ),
                "1,\"2\"\"word\"\"3\",4",
                separator,
                quote,
                escape
            );
        }

        @ParameterizedTest
        @ArgumentsSource(SeparatorsProvider.class)
        void testReadSeparatorInsideQuotes(@NotNull String separator, @NotNull String quote, @NotNull String escape) throws Exception {
            assertReadAll(
                rows(
                    row("a", "hello,world", "c"),
                    row("1", "2", "3")
                ),
                "a,\"hello,world\",c\n1,2,3",
                separator,
                quote,
                escape
            );

            assertReadAll(
                rows(
                    row("1", "2,\n3", "4")
                ),
                "1,\"2,\n3\",4",
                separator,
                quote,
                escape
            );

            assertReadAll(
                rows(
                    row("a", "b\n,c", "d")
                ),
                "a,\"b\n,c\",d",
                separator,
                quote,
                escape
            );
        }

        @Test
        public void testUnfinishedQuotationShouldThrow() {
            String csv = "a," + DEFAULT_QUOTE + "b";
            CSVReader reader = new CSVReader(new StringReader(csv), DEFAULT_SEPARATOR, DEFAULT_QUOTE, DEFAULT_ESCAPE);
            assertThrows(IOException.class, reader::readAll);
        }

    }

    @Nested
    class EscapeTests {

        @ParameterizedTest
        @ArgumentsSource(SeparatorsProvider.class)
        void testEscapeNotInQuotesIsRegularChar(@NotNull String separator, @NotNull String quote, @NotNull String escape) throws Exception {
            assertReadAll(
                rows(
                    row("1", "\\2", "3")
                ),
                "1,\\2,3",
                separator,
                quote,
                escape
            );

            assertReadAll(
                rows(
                    row("1", "2\\", "3")
                ),
                "1,2\\,3",
                separator,
                quote,
                escape
            );

            // not started quotes are not escaped
            assertReadAll(
                rows(
                    row("1", "2\\\"3,4\"", "5")
                ),
                "1,2\\\"3,4\",5",
                separator,
                quote,
                escape
            );

            assertReadAll(
                rows(
                    row("1", "\\", "2")
                ),
                "1,\\,2",
                separator,
                quote,
                escape
            );

            assertReadAll(
                rows(
                    row("\\", "\\", "1")
                ),
                "\\,\\,1",
                separator,
                quote,
                escape
            );
            assertReadAll(
                rows(
                    row("\\", "\\", "\\")
                ),
                "\\,\\,\\",
                separator,
                quote,
                escape
            );

            // double escape also just chars
            assertReadAll(
                rows(
                    row("\\\\", "\\\\", "\\\\")
                ),
                "\\\\,\\\\,\\\\",
                separator,
                quote,
                escape
            );
        }

        @ParameterizedTest
        @ArgumentsSource(SeparatorsProvider.class)
        void testEscapedQuotes(@NotNull String separator, @NotNull String quote, @NotNull String escape) throws Exception {
            assertReadAll(
                rows(
                    row("1", "2\"3\"", "4")
                ),
                "1,\"2\\\"3\\\"\",4",
                separator,
                quote,
                escape
            );

            assertReadAll(
                rows(
                    row("1", "2\"\"", "3")
                ),
                "1,\"2\\\"\\\"\",3",
                separator,
                quote,
                escape
            );

            // next line treated correctly
            assertReadAll(
                rows(
                    row("1", "2\"\n\"3", "4")
                ),
                "1,\"2\\\"\n\\\"3\",4",
                separator,
                quote,
                escape
            );

            // separator line treated correctly
            assertReadAll(
                rows(
                    row("1", "2\"4,5\"6", "7")
                ),
                "1,\"2\\\"4,5\\\"6\",7",
                separator,
                quote,
                escape
            );
        }

        @ParameterizedTest
        @ArgumentsSource(SeparatorsProvider.class)
        void testEscapeInQuotesBeforeUnescapableCharIsNormalChar(@NotNull String separator, @NotNull String quote, @NotNull String escape)
        throws Exception {
            assertReadAll(
                rows(
                    row("1", "\\2", "3")
                ),
                "1,\"\\\\2\",3",
                separator,
                quote,
                escape
            );
        }

        @ParameterizedTest
        @ArgumentsSource(SeparatorsProvider.class)
        void testEscapedEscapeBeforeQuotes(@NotNull String separator, @NotNull String quote, @NotNull String escape)
        throws Exception {
            assertReadAll(
                rows(
                    row("1", "2\\", "3")
                ),
                "1,\"2\\\\\",\"3\"",
                separator,
                quote,
                escape
            );

            assertReadAll(
                rows(
                    row("1", "2\\\\", "3")
                ),
                "1,\"2\\\\\\\\\",\"3\"",
                separator,
                quote,
                escape
            );
        }
    }

    @Nested
    class IgnoreLeadingWhiteSpaceTests {

        public static final String LEADING_SPACE = "   ";

        @ParameterizedTest
        @ArgumentsSource(SeparatorsProvider.class)
        void testIgnoresLeadingWhitespaceBeforeQuote(
            @NotNull String separator,
            @NotNull String quote,
            @NotNull String escape
        ) throws Exception {
            assertReadAll(
                rows(
                    row("1", "2", "3")
                ),
                "1," + LEADING_SPACE + "\"2\",3",
                separator,
                quote,
                escape,
                CSVReader.DEFAULT_SKIP_LINES,
                false,
                true
            );

            // not ignored inside quote
            assertReadAll(
                rows(
                    row("1", LEADING_SPACE + "2", "3")
                ),
                "1,\"" + LEADING_SPACE + "2\",3",
                separator,
                quote,
                escape,
                CSVReader.DEFAULT_SKIP_LINES,
                false,
                true
            );
        }

        @ParameterizedTest
        @ArgumentsSource(SeparatorsProvider.class)
        void testDoesNotIgnoreLeadingWhitespaceNotBeforeQuote(
            @NotNull String separator,
            @NotNull String quote,
            @NotNull String escape
        ) throws Exception {

            assertReadAll(
                rows(
                    row("1", LEADING_SPACE + "2", "3")
                ),
                "1," + LEADING_SPACE + "2,3",
                separator,
                quote,
                escape,
                CSVReader.DEFAULT_SKIP_LINES,
                false,
                true
            );
        }

        @ParameterizedTest
        @ArgumentsSource(SeparatorsProvider.class)
        void testReadAllDoesNotIgnoreLeadingWhitespaceBeforeQuote(
            @NotNull String separator,
            @NotNull String quote,
            @NotNull String escape
        ) throws Exception {
            assertReadAll(
                rows(
                    row("1", LEADING_SPACE + "\"2\"", "3")
                ),
                "1," + LEADING_SPACE + "\"2\",3",
                separator,
                quote,
                escape,
                CSVReader.DEFAULT_SKIP_LINES,
                false,
                false
            );
        }
    }

    @Nested
    class StrictQuotationTests {

        @ParameterizedTest
        @ArgumentsSource(SeparatorsProvider.class)
        void testIgnoresCharactersOutsideQuotes(
            @NotNull String separator,
            @NotNull String quote,
            @NotNull String escape
        ) throws Exception {

            assertReadAll(
                rows(
                    row("2")
                ),
                "123\"2\"456",
                separator,
                quote,
                escape,
                CSVReader.DEFAULT_SKIP_LINES,
                true,
                CSVParser.DEFAULT_IGNORE_LEADING_WHITESPACE
            );

            assertReadAll(
                rows(
                    row("", "2", "")
                ),
                "1,\"2\",3",
                separator,
                quote,
                escape,
                CSVReader.DEFAULT_SKIP_LINES,
                true,
                CSVParser.DEFAULT_IGNORE_LEADING_WHITESPACE
            );
        }

        @ParameterizedTest
        @ArgumentsSource(SeparatorsProvider.class)
        void testKeepsCharactersOutsideQuotesWhenStrictQuotesDisabled(
            @NotNull String separator,
            @NotNull String quote,
            @NotNull String escape
        ) throws Exception {

            assertReadAll(
                rows(
                    row("1", "2", "3")
                ),
                "1,2,3",
                separator,
                quote,
                escape,
                CSVReader.DEFAULT_SKIP_LINES,
                false,
                CSVParser.DEFAULT_IGNORE_LEADING_WHITESPACE
            );

            assertReadAll(
                rows(
                    row("123\"4\"56")
                ),
                "123\"4\"56",
                separator,
                quote,
                escape,
                CSVReader.DEFAULT_SKIP_LINES,
                false,
                CSVParser.DEFAULT_IGNORE_LEADING_WHITESPACE
            );
        }
    }

    private void assertReadAll(
        @NotNull List<List<String>> expectedTemplate,
        @NotNull String csvTemplate,
        @Nullable String separator,
        @Nullable String quoteChar,
        @Nullable String escape
    ) throws Exception {
        assertReadAll(
            expectedTemplate,
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
        @NotNull List<List<String>> expectedTemplate,
        @NotNull String csvTemplate,
        @Nullable String separator,
        @Nullable String quoteChar,
        @Nullable String escape,
        int line,
        boolean strictQuotes,
        boolean ignoreLeadingWhiteSpace
    ) throws Exception {

        var csv = replaceInCSVToCustom(csvTemplate, separator, quoteChar, escape);
        List<String[]> expected = replaceInExpected(expectedTemplate, separator, quoteChar, escape);
        try (CSVReader reader = createReader(csv, separator, quoteChar, escape, line, strictQuotes, ignoreLeadingWhiteSpace)) {
            List<String[]> actual = reader.readAll();

            assertEquals(
                expected.size(),
                actual.size(),
                "Differed rows length\nCSV: " + csv + "\nExpected:" + expected.stream().map(Arrays::toString).collect(Collectors.joining())
                    + "\nActual  :" + actual.stream().map(Arrays::toString).collect(Collectors.joining())
            );
            for (int i = 0; i < expected.size(); i++) {
                assertArrayEquals(
                    expected.get(i), actual.get(i), "\nCSV: " + csv + "\nExpected: "
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
        // to replace from the shortest one, not to cause conflicts
        Map<String, String> orderedSeparators = new TreeMap<>(
            Comparator.comparing(String::length).thenComparing(Comparator.naturalOrder())
        );
        if (separator != null && !separator.equals(DEFAULT_SEPARATOR)) {
            orderedSeparators.put(separator, DEFAULT_SEPARATOR);
        }
        if (quoteChar != null && !quoteChar.equals(DEFAULT_QUOTE)) {
            orderedSeparators.put(quoteChar, DEFAULT_QUOTE);
        }
        if (escape != null && !escape.equals(DEFAULT_QUOTE)) {
            orderedSeparators.put(escape, DEFAULT_ESCAPE);
        }

        String csv = csvTemplate;
        for (Map.Entry<String, String> replacement : orderedSeparators.entrySet()) {
            csv = csv.replace(replacement.getValue(), replacement.getKey());
        }
        return csv;
    }

    @NotNull
    private CSVReader createReader(
        @NotNull String csv,
        @Nullable String separator,
        @Nullable String quoteChar,
        @Nullable String escape,
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
    private List<List<String>> rows(List<String>... rows) {
        return Arrays.asList(rows);
    }

    @NotNull
    private List<String> row(String... values) {
        return Arrays.asList(values);
    }

    @NotNull
    private List<String[]> replaceInExpected(
        @NotNull List<List<String>> rows,
        @Nullable String separator,
        @Nullable String quoteChar,
        @Nullable String escape
    ) {
        return rows
            .stream()
            .map(r -> replaceExpected(r, separator, quoteChar, escape))
            .collect(Collectors.toList());

    }

    @NotNull
    private String[] replaceExpected(
        @NotNull List<String> row,
        @Nullable String separator,
        @Nullable String quoteChar,
        @Nullable String escape
    ) {
        return row
            .stream()
            .map(r -> replaceInCSVToCustom(r, separator, quoteChar, escape))
            .toArray(String[]::new);

    }

    private static class SeparatorsProvider implements ArgumentsProvider {

        @Override
        @NotNull
        public Stream<? extends Arguments> provideArguments(ParameterDeclarations parameters, ExtensionContext context) throws Exception {
            return provideSeparators();
        }

        @NotNull
        private static Stream<Arguments> provideSeparators() {
            return Stream.of(
                // defaults
                Arguments.of(DEFAULT_SEPARATOR, DEFAULT_QUOTE, DEFAULT_ESCAPE),
                // alternative one char
                Arguments.of(ALTERNATIVE_SEPARATOR, ALTERNATIVE_QUOTE, ALTERNATIVE_ESCAPE),
                // default + alt 2 chars
                Arguments.of(
                    DEFAULT_SEPARATOR + ALTERNATIVE_SEPARATOR,
                    DEFAULT_QUOTE + ALTERNATIVE_QUOTE,
                    DEFAULT_ESCAPE + ALTERNATIVE_ESCAPE
                ),
                // all special chars but starts same all cases. alternative for better visibility
                Arguments.of(ALTERNATIVE_SEPARATOR, ALTERNATIVE_SEPARATOR + ALTERNATIVE_QUOTE, ALTERNATIVE_SEPARATOR + ALTERNATIVE_ESCAPE),
                Arguments.of(ALTERNATIVE_QUOTE + ALTERNATIVE_SEPARATOR, ALTERNATIVE_QUOTE, ALTERNATIVE_QUOTE + ALTERNATIVE_ESCAPE),
                Arguments.of(
                    ALTERNATIVE_ESCAPE + ALTERNATIVE_QUOTE + ALTERNATIVE_SEPARATOR,
                    ALTERNATIVE_ESCAPE + ALTERNATIVE_QUOTE,
                    ALTERNATIVE_ESCAPE
                )
            );
        }
    }

}