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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    public static final CharSequence DEFAULT_SEPARATOR = ",";
    /**
     * The average size of a line read by openCSV (used for setting the size of StringBuilders).
     */
    public static final int INITIAL_READ_SIZE = 128;
    /**
     * The default quote character to use if none is supplied to the
     * constructor.
     */
    public static final CharSequence DEFAULT_QUOTE_CHARACTER = "\"";
    /**
     * The default escape character to use if none is supplied to the
     * constructor.
     */
    public static final CharSequence DEFAULT_ESCAPE_CHARACTER = "\\";
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
    public static final CharSequence NULL_CHARACTER = "\0";
    /**
     * Denotes what field contents will cause the parser to return null:  EMPTY_SEPARATORS, EMPTY_QUOTES, BOTH, NEITHER (default)
     */
    public static final CSVReaderNullFieldIndicator DEFAULT_NULL_FIELD_INDICATOR = CSVReaderNullFieldIndicator.NEITHER;

    /**
     * This is the character that the CSVParser will treat as the separator.
     */
    private final CharSequence separator;
    /**
     * This is the character that the CSVParser will treat as the quotation character.
     */
    private final CharSequence quotechar;
    /**
     * This is the character that the CSVParser will treat as the escape character.
     */
    private final CharSequence escape;
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
    private String pending;

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
        @NotNull CharSequence separator,
        @NotNull CharSequence quotechar,
        @NotNull CharSequence escape,
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
        @NotNull CharSequence separator,
        @NotNull CharSequence quotechar,
        @NotNull CharSequence escape,
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
        @NotNull CharSequence separator,
        @NotNull CharSequence quotechar,
        @NotNull CharSequence escape,
        boolean strictQuotes,
        boolean ignoreLeadingWhiteSpace,
        boolean ignoreQuotations, CSVReaderNullFieldIndicator nullFieldIndicator
    ) {
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
    }


    /**
     * @return The default separator for this parser.
     */
    @NotNull
    public CharSequence getSeparator() {
        return separator;
    }

    /**
     * @return The default quotation character for this parser.
     */
    @NotNull
    public CharSequence getQuotechar() {
        return quotechar;
    }

    /**
     * @return The default escape character for this parser.
     */
    @NotNull
    public CharSequence getEscape() {
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
        @NotNull CharSequence separator,
        @NotNull CharSequence quotechar,
        @NotNull CharSequence escape
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
    private boolean isSameCharacter(@NotNull CharSequence c1, @NotNull CharSequence c2) {
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
    private String[] parseLine(String nextLine, boolean multi) throws IOException {

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

        List<String> tokensOnThisLine = new ArrayList<>();
        StringBuilder sb = new StringBuilder(INITIAL_READ_SIZE);
        boolean inQuotes = false;
        boolean fromQuotedField = false;
        if (pending != null) {
            sb.append(pending);
            pending = null;
            // pending could only left after quotes on prev line
            inQuotes = true;
        }
        for (int i = 0; i < nextLine.length(); i++) {

            char c = nextLine.charAt(i);
            // escape works only inside quotes
            if (isEscapeChar(i, nextLine)) {
                i += escape.length();
                if (inQuotes(inQuotes) && isQuoteChar(i, nextLine)) {
                    sb.append(quotechar);
                    i += quotechar.length();
                } else {
                    sb.append(escape);
                }
            } else if (isQuoteChar(i, nextLine)) {
                if (inQuotes(inQuotes)) {
                    i += quotechar.length();
                    // double quotes "" inside quotes "a""bc" must be escaped -> a"b according to: https://www.rfc-editor.org/rfc/rfc4180.txt
                    if (nextLine.length() < (i + 1) && isQuoteChar(i + 1, nextLine)) {
                        sb.append(quotechar);
                        i++;
                        i += quotechar.length();
                    } else {
                        inQuotes = false;
                        fromQuotedField = true;
                    }
                } else {
                    inQuotes = true;
                    if (ignoreLeadingWhiteSpace && sb.length() > 0 && isAllWhiteSpace(sb)) {
                        sb.setLength(0);
                    } else {
                        sb.append(c);
                    }
                }
            } else if (isSpecialChar(i, nextLine, separator)) {
                i += separator.length() - 1;
                if (!inQuotes(inQuotes)) {
                    tokensOnThisLine.add(convertEmptyToNullIfNeeded(sb.toString(), fromQuotedField));
                    sb.setLength(0);
                    fromQuotedField = false;
                } else {
                    // separator inside quotes is normal char
                    sb.append(separator);
                }
            } else {
                if (!strictQuotes || inQuotes(inQuotes)) {
                    sb.append(c);
                }
            }
        }
        // line is done - check status
        if (inQuotes(inQuotes)) {
            if (multi) {
                // continuing a quoted section, re-append newline
                sb.append('\n');
                pending = sb.toString();
                sb = null; // this partial content is not to be added to field list yet
            } else {
                throw new IOException("Un-terminated quoted field at end of CSV line");
            }
        }
        if (sb != null) {
            tokensOnThisLine.add(convertEmptyToNullIfNeeded(sb.toString(), fromQuotedField));
        }
        return tokensOnThisLine.toArray(new String[tokensOnThisLine.size()]);

    }

    @Nullable
    private String convertEmptyToNullIfNeeded(@NotNull String s, boolean fromQuotedField) {
        if (s.isEmpty() && shouldConvertEmptyToNull(fromQuotedField)) {
            return null;
        }
        return s;
    }

    private boolean shouldConvertEmptyToNull(boolean fromQuotedField) {
        switch (nullFieldIndicator) {
            case BOTH:
                return true;
            case EMPTY_SEPARATORS:
                return !fromQuotedField;
            case EMPTY_QUOTES:
                return fromQuotedField;
            default:
                return false;
        }
    }

    /**
     * Determines if we can process as if we were in quotes.
     *
     * @param inQuotes - are we currently in quotes.
     * @return - true if we should process as if we are inside quotes.
     */
    private boolean inQuotes(boolean inQuotes) {
        return (inQuotes && !ignoreQuotations);
    }

    private boolean isQuoteChar(int index, @NotNull String line) {
        return isSpecialChar(index, line, quotechar);
    }


    private boolean isEscapeChar(int index, @NotNull String line) {
        return isSpecialChar(index, line, escape);
    }

    private boolean isSpecialChar(int index, @NotNull String line, @NotNull CharSequence specialChar) {
        for (int i = 0; i < specialChar.length(); i++) {
            if (specialChar.charAt(i) != line.charAt(index)) {
                return false;
            }
            index++;
        }
        return true;
    }

    /**
     * Checks to see if the character after the current index in a String is an escapable character.
     * Meaning the next character is either a quotation character or the escape char and you are inside
     * quotes.
     * <p>
     * precondition: the current character is an escape
     *
     * @param nextLine the current line
     * @param inQuotes true if the current context is quoted
     * @param i        current index in line
     * @return true if the following character is a quote
     */
    protected boolean isNextCharacterEscapable(int i, @NotNull String nextLine, boolean inQuotes) {
        return inQuotes  // we are in quotes, therefore there can be escaped quotes in here.
            && nextLine.length() > (i + 1); // there is indeed another character to check;
    }

    private boolean isCharacterEscapable(int i, @NotNull String nextLine) {
        return isQuoteChar(i, nextLine) || isEscapeChar(i, nextLine);
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
}
