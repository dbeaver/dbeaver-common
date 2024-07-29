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
package org.jkiss.utils;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Date;
import java.util.Locale;

public class GsonUtils {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
        .ofPattern("MMM d, yyyy[, h:mm:ss a]")
        .localizedBy(Locale.ENGLISH)
        .withZone(ZoneId.of("UTC"));

    public static GsonBuilder gsonBuilder() {
        return new GsonBuilder()
            .registerTypeHierarchyAdapter(byte[].class, new ByteArrayToBase64TypeAdapter())
            .registerTypeHierarchyAdapter(Date.class, new DateTypeAdapter())
            .serializeNulls()
            .enableComplexMapKeySerialization()
            .setPrettyPrinting();
    }

    public static class ByteArrayToBase64TypeAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {
        public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return Base64.decode(json.getAsString());
        }

        public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(Base64.encode(src));
        }
    }

    public static class DateTypeAdapter implements JsonSerializer<Date>, JsonDeserializer<Date> {
        @Override
        public synchronized JsonElement serialize(Date date, Type type, JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(LocalDateTime.ofInstant(date.toInstant(), ZoneId.of("UTC")).format(DATE_TIME_FORMATTER));
        }

        @Override
        public synchronized Date deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) {
            TemporalAccessor accessor = DATE_TIME_FORMATTER.parse(jsonElement.getAsString());
            LocalDate localDate = accessor.query(TemporalQueries.localDate());
            LocalTime localTime = accessor.query(TemporalQueries.localTime());
            if (localTime != null) {
                return Date.from(LocalDateTime.of(localDate, localTime).toInstant(ZoneOffset.UTC));
            } else {
                return Date.from(localDate.atStartOfDay().toInstant(ZoneOffset.UTC));
            }
        }
    }
}
