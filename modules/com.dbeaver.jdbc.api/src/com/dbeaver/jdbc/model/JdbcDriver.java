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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

public abstract class JdbcDriver implements Driver {

    private static final Logger rootLogger = Logger.getLogger(JdbcDriver.class.getName());

    private String driverUrlPrefix;

    public JdbcDriver(String driverUrlPrefix) {
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
