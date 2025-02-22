/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp
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

import org.jkiss.utils.StandardConstants;

import java.text.SimpleDateFormat;

public abstract class CommonJdbcConstants {

    public static final SimpleDateFormat ISO_TIMESTAMP_FORMAT = new SimpleDateFormat(StandardConstants.ISO_TIMESTAMP_PATTERN);
    public static final SimpleDateFormat ISO_TIME_FORMAT = new SimpleDateFormat(StandardConstants.ISO_TIME_PATTERN);
    public static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat(StandardConstants.ISO_DATE_PATTERN);

    public static final String DRIVERS_VENDOR = "com.dbeaver.jdbc";
}
