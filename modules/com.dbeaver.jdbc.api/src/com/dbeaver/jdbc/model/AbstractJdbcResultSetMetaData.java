/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp
 *
 * All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of DBeaver Corp and its suppliers, if any.
 * The intellectual and technical concepts contained
 * herein are proprietary to DBeaver Corp and its suppliers
 * and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from DBeaver Corp.
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
