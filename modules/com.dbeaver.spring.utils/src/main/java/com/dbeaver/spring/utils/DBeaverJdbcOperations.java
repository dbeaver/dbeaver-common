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
package com.dbeaver.spring.utils;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

public class DBeaverJdbcOperations {
    @NotNull
    private final DataSource dataSource;

    public DBeaverJdbcOperations(
        @NotNull DataSource dataSource
    ) {
        this.dataSource = dataSource;
    }

    @FunctionalInterface
    public interface DBeaverJdbcTransactionCallback<T> {
        T execute(
            @NotNull NamedParameterJdbcTemplate jdbc
        ) throws Exception;
    }

    public <T> T executeInTransaction(
        @NotNull DBeaverJdbcTransactionCallback<T> callback
    ) throws DBException {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            Exception failure = null;
            try {
                NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(
                    new SingleConnectionDataSource(connection, true)
                );
                T result = callback.execute(jdbc);
                connection.commit();
                return result;
            } catch (Exception e) {
                failure = e;
                rollback(connection, e);
                throw toDBException(e);
            } finally {
                restoreAutoCommit(connection, autoCommit, failure);
            }
        } catch (SQLException e) {
            throw new DBException(e.getMessage(), e);
        }
    }

    private void rollback(
        @NotNull Connection connection,
        @NotNull Exception cause
    ) throws DBException {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            cause.addSuppressed(rollbackException);
            throw new DBException(cause.getMessage(), cause);
        }
    }

    private void restoreAutoCommit(
        @NotNull Connection connection,
        boolean autoCommit,
        @Nullable Exception failure
    ) throws DBException {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException e) {
            if (failure != null) {
                failure.addSuppressed(e);
                return;
            }
            throw new DBException(e.getMessage(), e);
        }
    }

    @NotNull
    private DBException toDBException(
        @NotNull Exception exception
    ) {
        if (exception instanceof DBException ddException) {
            return ddException;
        }
        return new DBException(exception.getMessage(), exception);
    }
}
