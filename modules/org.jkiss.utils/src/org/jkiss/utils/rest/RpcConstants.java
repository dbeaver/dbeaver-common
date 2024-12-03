/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
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
package org.jkiss.utils.rest;

import com.google.gson.*;
import org.jkiss.utils.StandardConstants;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RpcConstants {
    public static final int SC_OK = 200;
    public static final int SC_FORBIDDEN = 403;
    public static final int SC_UNSUPPORTED = 405;
    public static final int SC_NOT_FOUND = 404;
    public static final int SC_SERVER_ERROR = 500;

    public static final DateTimeFormatter ISE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern(
        StandardConstants.ISO_TIMESTAMP_PATTERN);
    public static final DateTimeFormatter ISE_DATE_FORMATTER = DateTimeFormatter.ofPattern(
        StandardConstants.ISO_DATE_PATTERN);

    public static class LocalDateAdapter implements JsonSerializer<LocalDate> {
        public JsonElement serialize(LocalDate date, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(date.format(ISE_DATE_FORMATTER)); // "yyyy-mm-dd"
        }
    }
    public static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime> {
        public JsonElement serialize(LocalDateTime date, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(date.format(ISE_TIMESTAMP_FORMATTER)); // "yyyy-mm-dd"
        }
    }

    public static final Gson DEFAULT_GSON = new GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .disableHtmlEscaping()
        .serializeNulls()
        .create();

    public static final Gson COMPACT_GSON = new GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .setDateFormat(StandardConstants.ISO_TIMESTAMP_PATTERN)
        .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
        .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
        .disableHtmlEscaping()
        .create();


}
