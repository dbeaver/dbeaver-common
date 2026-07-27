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

/*
 * This package contains a slightly modified version of opencsv library
 * without unwanted functionality and dependencies, licensed under Apache 2.0.
 *
 * See https://search.maven.org/artifact/com.opencsv/opencsv/3.4/bundle
 * See http://opencsv.sf.net/
 */
package org.jkiss.utils.csv;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * A very simple CSV parser released under a commercial-friendly license.
 * This just implements splitting a single line into fields.
 *
 * @author Glen Smith
 * @author Rainer Pruy
 */

public class CSVParser {

    /**
     * The default separator to use if none is supplied to the constructor.
     */
    public static final String DEFAULT_SEPARATOR = ",";
    /**
     * The average size of a line read by openCSV (used for setting the size of StringBuilders).
     */
    public static final int INITIAL_READ_SIZE = 128;
    /**
     * The default quote character to use if none is supplied to the
     * constructor.
     */
    public static final String DEFAULT_QUOTE_CHARACTER = "\"";
    /**
     * The default escape character to use if none is supplied to the
     * constructor.
     */
    public static final String DEFAULT_ESCAPE_CHARACTER = "\\";
    /**
     * The default strict quote behavior to use if none is supplied to the
     * constructor.
     */
    public static final boolean DEFAULT_STRICT_QUOTES = false;
    /**
     * The default leading whitespace behavior to use if none is supplied to the
     * constructor.
     */
    public static final boolean DEFAULT_IGNORE_LEADING_WHITESPACE = true;
    /**
     * If the quote character is set to null then there is no quote character.
     */
    public static final boolean DEFAULT_IGNORE_QUOTATIONS = false;
    /**
     * This is the "null" character - if a value is set to this then it is ignored.
     */
    public static final String NULL_CHARACTER = "\0";
    /**
     * Denotes what field contents will cause the parser to return null:  EMPTY_SEPARATORS, EMPTY_QUOTES, BOTH, NEITHER (default)
     */
    public static final CSVReaderNullFieldIndicator DEFAULT_NULL_FIELD_INDICATOR = CSVReaderNullFieldIndicator.NEITHER;

    /**
     * This is the character that the CSVParser will treat as the separator.
     */
    private final String separator;
    /**
     * This is the character that the CSVParser will treat as the quotation character.
     */
    private final String quotechar;
    /**
     * This is the character that the CSVParser will treat as the escape character.
     */
    private final String escape;
    /**
     * Determines if the field is between quotes (true) or between separators (false).
     */
    private final boolean strictQuotes;
    /**
     * Ignore any leading white space at the start of the field.
     */
    private final boolean ignoreLeadingWhiteSpace;
    /**
     * Skip over quotation characters when parsing.
     */
    private final boolean ignoreQuotations;
    private final CSVReaderNullFieldIndicator nullFieldIndicator;
    // special chars must be parsed from the longest one
    private final List<Pair<String, CharacterStrategy>> orderedSpecialChars;

    @Nullable
    private String pending;

    private final List<String> tokensOnThisLine = new ArrayList<>(INITIAL_READ_SIZE);
    private String currentLine;
    private StringBuilder currentToken;
    private boolean inQuotes;
    // the tricky case of an embedded quote in the middle: a,b"c"d,e
    private boolean quotesInField;
    private boolean lastTokenFromQuotedField;

    /**
     * Constructs CSVParser with supplied separator and quote char.
     * Allows setting the "strict quotes" and "ignore leading whitespace" flags
     *
     * @param separator               the delimiter to use for separating entries
     * @param quotechar               the character to use for quoted elements
     * @param escape                  the character to use for escaping a separator or quote
     * @param strictQuotes            if true, characters outside the quotes are ignored
     * @param ignoreLeadingWhiteSpace if true, white space in front of a quote in a field is ignored
     */
    public CSVParser(
        @NotNull String separator,
        @NotNull String quotechar,
        @NotNull String escape,
        boolean strictQuotes,
        boolean ignoreLeadingWhiteSpace
    ) {
        this(separator, quotechar, escape, strictQuotes, ignoreLeadingWhiteSpace, DEFAULT_IGNORE_QUOTATIONS);
    }

    /**
     * Constructs CSVParser with supplied separator and quote char.
     * Allows setting the "strict quotes" and "ignore leading whitespace" flags
     *
     * @param separator               the delimiter to use for separating entries
     * @param quotechar               the character to use for quoted elements
     * @param escape                  the character to use for escaping a separator or quote
     * @param strictQuotes            if true, characters outside the quotes are ignored
     * @param ignoreLeadingWhiteSpace if true, white space in front of a quote in a field is ignored
     * @param ignoreQuotations        if true, treat quotations like any other character.
     */
    public CSVParser(
        @NotNull String separator,
        @NotNull String quotechar,
        @NotNull String escape,
        boolean strictQuotes,
        boolean ignoreLeadingWhiteSpace,
        boolean ignoreQuotations
    ) {
        this(separator, quotechar, escape, strictQuotes, ignoreLeadingWhiteSpace, ignoreQuotations, DEFAULT_NULL_FIELD_INDICATOR);
    }

    /**
     * Constructs CSVParser with supplied separator and quote char.
     * Allows setting the "strict quotes" and "ignore leading whitespace" flags
     *
     * @param separator               the delimiter to use for separating entries
     * @param quotechar               the character to use for quoted elements
     * @param escape                  the character to use for escaping a separator or quote
     * @param strictQuotes            if true, characters outside the quotes are ignored
     * @param ignoreLeadingWhiteSpace if true, white space in front of a quote in a field is ignored
     * @param ignoreQuotations        if true, treat quotations like any other character.
     * @param nullFieldIndicator      which field content will be returned as null: EMPTY_SEPARATORS, EMPTY_QUOTES,
     *                                BOTH, NEITHER (default)
     */
    CSVParser(
        @NotNull String separator,
        @NotNull String quotechar,
        @NotNull String escape,
        boolean strictQuotes,
        boolean ignoreLeadingWhiteSpace,
        boolean ignoreQuotations, CSVReaderNullFieldIndicator nullFieldIndicator
    ) {
        if (CommonUtils.isEmpty(separator) || CommonUtils.isEmpty(quotechar) || CommonUtils.isEmpty(escape)) {
            throw new UnsupportedOperationException("None of separator, quote, and escape characters can be empty");
        }
        if (anyCharactersAreTheSame(separator, quotechar, escape)) {
            throw new UnsupportedOperationException("The separator, quote, and escape characters must be different!");
        }
        if (NULL_CHARACTER.equals(separator)) {
            throw new UnsupportedOperationException("The separator character must be defined!");
        }
        this.separator = separator;
        this.quotechar = quotechar;
        this.escape = escape;
        this.strictQuotes = strictQuotes;
        this.ignoreLeadingWhiteSpace = ignoreLeadingWhiteSpace;
        this.ignoreQuotations = ignoreQuotations;
        this.nullFieldIndicator = nullFieldIndicator;

        this.orderedSpecialChars =
            Stream.of(
                Pair.of(separator, CharacterStrategy.SEPARATOR),
                Pair.of(quotechar, CharacterStrategy.QUOTES),
                Pair.of(escape, CharacterStrategy.ESCAPE)
            ).sorted(Comparator.comparingInt((Pair<String, CharacterStrategy> p) -> p.getFirst().length())
                .reversed()
                .thenComparing(Pair::getFirst)
            ).toList();
    }


    /**
     * @return The default separator for this parser.
     */
    @NotNull
    public String getSeparator() {
        return separator;
    }

    /**
     * @return The default quotation character for this parser.
     */
    @NotNull
    public String getQuotechar() {
        return quotechar;
    }

    /**
     * @return The default escape character for this parser.
     */
    @NotNull
    public String getEscape() {
        return escape;
    }

    /**
     * @return The default strictQuotes setting for this parser.
     */
    public boolean isStrictQuotes() {
        return strictQuotes;
    }

    /**
     * @return The default ignoreLeadingWhiteSpace setting for this parser.
     */
    public boolean isIgnoreLeadingWhiteSpace() {
        return ignoreLeadingWhiteSpace;
    }

    /**
     * @return the default ignoreQuotation setting for this parser.
     */
    public boolean isIgnoreQuotations() {
        return ignoreQuotations;
    }

    /**
     * checks to see if any two of the three characters are the same.  This is because in openCSV the
     * separator, quote, and escape characters must the different.
     *
     * @param separator the defined separator character
     * @param quotechar the defined quotation cahracter
     * @param escape    the defined escape character
     * @return true if any two of the three are the same.
     */
    private boolean anyCharactersAreTheSame(
        @NotNull String separator,
        @NotNull String quotechar,
        @NotNull String escape
    ) {
        return isSameCharacter(separator, quotechar) || isSameCharacter(separator, escape) || isSameCharacter(quotechar, escape);
    }

    /**
     * checks that the two characters are the same and are not the defined NULL_CHARACTER.
     *
     * @param c1 first character
     * @param c2 second character
     * @return true if both characters are the same and are not the defined NULL_CHARACTER
     */
    private boolean isSameCharacter(@NotNull String c1, @NotNull String c2) {
        return !NULL_CHARACTER.equals(c1) && c1.equals(c2);
    }

    /**
     * @return true if something was left over from last call(s)
     */
    public boolean isPending() {
        return pending != null;
    }

    /**
     * Parses an incoming String and returns an array of elements.  This method is used when the
     * data spans multiple lines.
     *
     * @param nextLine current line to be processed
     * @return the comma-tokenized list of elements, or null if nextLine is null
     * @throws IOException if bad things happen during the read
     */
    @Nullable
    public String[] parseLineMulti(String nextLine) throws IOException {
        return parseLine(nextLine, true);
    }

    /**
     * Parses an incoming String and returns an array of elements.  This method is used when all data is contained
     * in a single line.
     *
     * @param nextLine Line to be parsed.
     * @return the comma-tokenized list of elements, or null if nextLine is null
     * @throws IOException if bad things happen during the read
     */
    @Nullable
    public String[] parseLine(String nextLine) throws IOException {
        return parseLine(nextLine, false);
    }

    /**
     * Parses an incoming String and returns an array of elements.
     *
     * @param nextLine the string to parse
     * @param multi    Does it take multiple lines to form a single record.
     * @return the comma-tokenized list of elements, or null if nextLine is null
     * @throws IOException if bad things happen during the read
     */
    @Nullable
    private String[] parseLine(@Nullable String nextLine, boolean multi) throws IOException {

        if (!multi && pending != null) {
            pending = null;
        }

        if (nextLine == null) {
            if (pending != null) {
                String s = pending;
                pending = null;
                return new String[] {s};
            } else {
                return null;
            }
        }
        resetLineTokens();
        currentLine = nextLine;
        int lineIndex = 0;
        while (lineIndex < nextLine.length()) {
            lineIndex += switch (readNextCharStrategy(lineIndex)) {
                case QUOTES -> writeQuotes(lineIndex);
                case ESCAPE -> writeEscape(lineIndex);
                case SEPARATOR -> writeSeparator();
                case SIMPLE_CHAR -> writeSimpleChar(lineIndex);
            };
        }
        // line is done - check status
        if (inQuotes()) {
            if (multi) {
                // continuing a quoted section, re-append newline
                currentToken.append('\n');
                pending = currentToken.toString();
                currentToken = null; // this partial content is not to be added to field list yet
            } else {
                throw new IOException("Un-terminated quoted field at end of CSV line");
            }
        }
        if (currentToken != null) {
            tokensOnThisLine.add(convertEmptyToNullIfNeeded(currentToken.toString()));
        }
        return tokensOnThisLine.toArray(new String[tokensOnThisLine.size()]);

    }

    private void resetLineTokens() {
        tokensOnThisLine.clear();
        currentToken = new StringBuilder(); // nullifying to make difference between null and empty string added
        currentLine = null;
        inQuotes = false;
        lastTokenFromQuotedField = false;
        if (pending != null) {
            currentToken.append(pending);
            pending = null;
            // pending could only left after quotes on prev line, if there are not ignored completely
            inQuotes = !ignoreQuotations;
        }
    }

    @NotNull
    private CharacterStrategy readNextCharStrategy(int lineIndex) {
        for (Pair<String, CharacterStrategy> strategyPair : orderedSpecialChars) {
            if (currentLine.startsWith(strategyPair.getFirst(), lineIndex)) {
                return strategyPair.getSecond();
            }
        }
        return CharacterStrategy.SIMPLE_CHAR;
    }


    @Nullable
    private String convertEmptyToNullIfNeeded(@NotNull String s) {
        if (s.isEmpty() && shouldConvertEmptyToNull()) {
            return null;
        }
        return s;
    }

    private boolean shouldConvertEmptyToNull() {
        return switch (nullFieldIndicator) {
            case BOTH -> true;
            case EMPTY_SEPARATORS -> !lastTokenFromQuotedField;
            case EMPTY_QUOTES -> lastTokenFromQuotedField;
            default -> false;
        };
    }

    private boolean inQuotes() {
        return (inQuotes && !ignoreQuotations);
    }

    /**
     * Checks if every element is the character sequence is whitespace.
     * <p>
     * precondition: sb.length() is greater than 0
     *
     * @param sb A sequence of characters to examine
     * @return true if every character in the sequence is whitespace
     */
    protected boolean isAllWhiteSpace(CharSequence sb) {
        for (int i = 0; i < sb.length(); i++) {
            if (!Character.isWhitespace(sb.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return - the null field indicator.
     */
    public CSVReaderNullFieldIndicator nullFieldIndicator() {
        return nullFieldIndicator;
    }

    private enum CharacterStrategy {
        ESCAPE,
        QUOTES,
        SEPARATOR,
        SIMPLE_CHAR;
    }

    private int writeEscape(int lineIndex) {
        int totalAppendedLength = escape.length();
        // escape not in quote is literal char
        if (inQuotes()) {
            CharacterStrategy specialChar = readNextCharStrategy(lineIndex + totalAppendedLength);
            if (specialChar.equals(CharacterStrategy.QUOTES)) {
                currentToken.append(quotechar);
                totalAppendedLength += quotechar.length();
            } else if (specialChar.equals(CharacterStrategy.ESCAPE)) {
                currentToken.append(escape);
                totalAppendedLength += escape.length();
            } else {
                currentToken.append(escape);
            }
        } else {
            currentToken.append(escape);
        }
        return totalAppendedLength;
    }

    private int writeQuotes(int lineIndex) {
        int totalAppendedLength = quotechar.length();
        if (inQuotes()) {
            // double quotes "" inside quotes "a""bc" must be escaped -> a"b according to: https://www.rfc-editor.org/rfc/rfc4180.txt
            CharacterStrategy specialChar = readNextCharStrategy(lineIndex + totalAppendedLength);
            if (specialChar.equals(CharacterStrategy.QUOTES)) {
                currentToken.append(quotechar);
                totalAppendedLength += quotechar.length();
            } else {
                inQuotes = false;
                lastTokenFromQuotedField = true;
                if (quotesInField) {
                    currentToken.append(quotechar);
                    quotesInField = false;
                }
            }
            // if ignore quotations - just skip quotation completely
        } else if (!ignoreQuotations) {
            inQuotes = true;
            if (ignoreLeadingWhiteSpace && !currentToken.isEmpty() && isAllWhiteSpace(currentToken)) {
                currentToken.setLength(0);
            }
            if (!currentToken.isEmpty()) {
                currentToken.append(quotechar);
                quotesInField = true;
            }
        }
        return totalAppendedLength;
    }

    private int writeSeparator() {
        if (inQuotes()) {
            currentToken.append(separator);
        } else {
            tokensOnThisLine.add(convertEmptyToNullIfNeeded(currentToken.toString()));
            lastTokenFromQuotedField = false;
            currentToken.setLength(0);
        }
        return separator.length();
    }

    private int writeSimpleChar(int lineIndex) {
        char currentChar = currentLine.charAt(lineIndex);
        if (!strictQuotes || inQuotes()) {
            currentToken.append(currentChar);
            lastTokenFromQuotedField = false;
        }
        return 1;
    }
}
