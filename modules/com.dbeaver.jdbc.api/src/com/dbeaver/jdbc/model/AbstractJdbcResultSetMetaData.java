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
package com.dbeaver.jdbc.model;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

public abstract class AbstractJdbcResultSetMetaData<STMT extends AbstractJdbcStatement<? extends AbstractJdbcConnection>> implements ResultSetMetaData {

    protected STMT statement;

    public AbstractJdbcResultSetMetaData(STMT statement) {
        this.statement = statement;
    }

    @Override
    public String getColumnClassName(int column) throws SQLException {
        return Object.class.getName();
    }

    @Override
    public <T> T unwrap(Class<T> iface) {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        } else {
            return null;
        }
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

}
