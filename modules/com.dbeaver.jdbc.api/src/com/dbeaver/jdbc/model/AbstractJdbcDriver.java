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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

public abstract class AbstractJdbcDriver implements Driver {

    private final Logger rootLogger;

    private final String driverUrlPrefix;

    public AbstractJdbcDriver(String driverUrlPrefix) {
        this.rootLogger = Logger.getLogger(getClass().getName());
        this.driverUrlPrefix = driverUrlPrefix;
    }

    //////////////////////////////////
    // JDBC API

    public String getDriverUrlPrefix() {
        return driverUrlPrefix;
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.toLowerCase().startsWith(driverUrlPrefix);
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        validateConnection(url, info);
        return connectImpl(url, info);
    }

    protected abstract Connection connectImpl(String url, Properties info) throws SQLException;

    protected void validateConnection(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            throw new SQLException("Invalid URL: " + url + ", expected prefix '" + getDriverUrlPrefix() + "'");
        }
        // Validate license
    }

    @Override
    public Logger getParentLogger() {
        return rootLogger;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    @Override
    public boolean jdbcCompliant() {
        return true;
    }

}
