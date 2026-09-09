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
package org.jkiss.api;

import org.jkiss.code.NotNull;

/**
 * Represents a complex reference to an object.
 * Consists of primary and secondary elements delimited with semicolon.
 */
public record CompositeObjectId(
    @NotNull String primaryId,
    @NotNull String secondaryId
) {
    @NotNull
    public static CompositeObjectId of(@NotNull String shortId) {
        String[] parts = shortId.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid object reference: " + shortId + ", must be in a form of primary-id:secondary-id");
        }
        return new CompositeObjectId(parts[0], parts[1]);
    }

    /**
     * A short identifier.
     */
    @NotNull
    public String shortId() {
        return primaryId + ':' + secondaryId;
    }

    @NotNull
    @Override
    public String toString() {
        return shortId();
    }

}
