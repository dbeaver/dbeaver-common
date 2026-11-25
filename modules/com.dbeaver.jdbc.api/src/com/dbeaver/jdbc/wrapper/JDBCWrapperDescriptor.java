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
package com.dbeaver.jdbc.wrapper;

import org.jkiss.code.NotNull;

import java.util.Objects;

/**
 * Identifies an implementation without loading its native dependencies in the application class loader.
 * The source class belongs to the bundle containing the implementation. Implementation classes and
 * their helpers must be in a dedicated package, separate from their public API.
 */
public record JDBCWrapperDescriptor(@NotNull Class<?> source, @NotNull String implementationClass) {
    public JDBCWrapperDescriptor {
        Objects.requireNonNull(source);
        Objects.requireNonNull(implementationClass);
        if (implementationClass.lastIndexOf('.') < 0) {
            throw new IllegalArgumentException("A wrapper implementation must have a dedicated package");
        }
    }
}
