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
package org.jkiss.api;

import org.jkiss.code.NotNull;

import java.util.Objects;

/**
 * Represents a reference to a driver. Instead of storing two separate fields,
 * it encapsulates them into one entity that is easily searchable through the code base.
 */
public final class DriverReference {
    public static final DriverReference UNKNOWN = new DriverReference("", "");
    @NotNull
    private final String providerId;
    @NotNull
    private final String driverId;

    /**
     * @param providerId id of the provider
     * @param driverId   id of the driver
     */
    public DriverReference(@NotNull String providerId, @NotNull String driverId) {
        this.providerId = providerId;
        this.driverId = driverId;
    }

    @NotNull
    public static DriverReference of(@NotNull String shortId) {
        String[] parts = shortId.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid driver reference: " + shortId + ", must be in a form of provider-id:driver-id");
        }
        return new DriverReference(parts[0], parts[1]);
    }

    /**
     * A short identifier consisting of both provider and driver identifiers: {@code provider-id:driver-id}.
     *
     * @return a short identifier of the driver
     */
    @NotNull
    public String shortId() {
        return providerId + ':' + driverId;
    }

    @Override
    public String toString() {
        return shortId();
    }

    @NotNull
    public String providerId() {
        return providerId;
    }

    @NotNull
    public String driverId() {
        return driverId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (DriverReference) obj;
        return Objects.equals(this.providerId, that.providerId) &&
            Objects.equals(this.driverId, that.driverId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, driverId);
    }

}
