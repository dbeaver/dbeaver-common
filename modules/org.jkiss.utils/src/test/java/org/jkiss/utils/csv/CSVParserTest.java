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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CSVParserTest {

    @Nested
    class IgnoreQuotationsTests {

        String csv = "1,\"2,3\",4";

        @Test
        public void quotationsIgnored() throws IOException {
            // given
            CSVParser csvParser = new CSVParser(
                CSVParser.DEFAULT_SEPARATOR,
                CSVParser.DEFAULT_QUOTE_CHARACTER,
                CSVParser.DEFAULT_ESCAPE_CHARACTER,
                CSVParser.DEFAULT_STRICT_QUOTES,
                CSVParser.DEFAULT_IGNORE_LEADING_WHITESPACE,
                true
            );
            // then
            assertArrayEquals(new String[]{"1", "2", "3", "4"}, csvParser.parseLine(csv));
        }

        @Test
        public void quotationsNotIgnored() throws IOException {
            // given
            CSVParser csvParser = new CSVParser(
                CSVParser.DEFAULT_SEPARATOR,
                CSVParser.DEFAULT_QUOTE_CHARACTER,
                CSVParser.DEFAULT_ESCAPE_CHARACTER,
                CSVParser.DEFAULT_STRICT_QUOTES,
                CSVParser.DEFAULT_IGNORE_LEADING_WHITESPACE,
                false
            );
            // then
            assertArrayEquals(new String[]{"1", "2,3", "4"}, csvParser.parseLine(csv));
        }
    }


    @Nested
    class NullFieldIndicatorTests {

        public static final String EMPTY_CSV = ",,\"\",";

        @Test
        void neitherDoesNotConvertEmptyFieldsToNull() throws IOException {
            assertNullFieldParsedCorrectly(
                CSVReaderNullFieldIndicator.NEITHER,
                new String[]{"", "", "", ""},
                EMPTY_CSV
            );
        }

        @Test
        void bothConvertsAllEmptyFieldsToNull() throws IOException {
            assertNullFieldParsedCorrectly(
                CSVReaderNullFieldIndicator.BOTH,
                new String[]{null, null, null, null},
                EMPTY_CSV
            );
        }

        @Test
        void emptySeparatorsConvertsOnlySeparatorGeneratedEmptiesToNull() throws IOException {
            assertNullFieldParsedCorrectly(
                CSVReaderNullFieldIndicator.EMPTY_SEPARATORS,
                new String[]{null, null, "", null},
                EMPTY_CSV
            );
        }

        @Test
        void emptyQuotesConvertsOnlyQuotedEmptiesToNull() throws IOException {
            assertNullFieldParsedCorrectly(
                CSVReaderNullFieldIndicator.EMPTY_QUOTES,
                new String[]{"", "", null, ""},
                EMPTY_CSV
            );
        }

        private void assertNullFieldParsedCorrectly(
            CSVReaderNullFieldIndicator nullFieldIndicator,
            @NotNull String[] expected,
            @NotNull String csv
        ) throws IOException {
            CSVParser parser = createNullFieldIndicatorParser(nullFieldIndicator);
            assertArrayEquals(expected, parser.parseLine(csv));
        }

        @NotNull
        private CSVParser createNullFieldIndicatorParser(
            @NotNull CSVReaderNullFieldIndicator nullFieldIndicator
        ) {
            return new CSVParser(
                CSVParser.DEFAULT_SEPARATOR,
                CSVParser.DEFAULT_QUOTE_CHARACTER,
                CSVParser.DEFAULT_ESCAPE_CHARACTER,
                CSVParser.DEFAULT_STRICT_QUOTES,
                CSVParser.DEFAULT_IGNORE_LEADING_WHITESPACE,
                CSVParser.DEFAULT_IGNORE_QUOTATIONS,
                nullFieldIndicator
            );
        }
    }

}