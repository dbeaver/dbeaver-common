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

import java.sql.SQLException;

/** Base contract for native JDBC adapters. Public adapter APIs must not expose native driver classes. */
public interface JDBCDriverWrapper {
    /**
     * Checks the actual driver loaded at runtime, before any operation with side effects.
     * Implementations must not open connections or register credentials here.
     *
     * @throws java.sql.SQLFeatureNotSupportedException if this adapter does not support the loaded driver version
     */
    void checkCompatibility() throws SQLException;
}
