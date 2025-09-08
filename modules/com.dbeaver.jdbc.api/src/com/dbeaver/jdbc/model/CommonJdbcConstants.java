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

    public static final String DBEAVER_VENDOR = "com.dbeaver.jdbc";

    // JDBC API constants for metadata result set column names

    public static final String CN_META_TABLE_CAT = "TABLE_CAT";
    public static final String CN_META_TABLE_SCHEM = "TABLE_SCHEM";
    public static final String CN_META_TABLE_NAME = "TABLE_NAME";
    public static final String CN_META_COLUMN_NAME = "COLUMN_NAME";
    public static final String CN_META_DATA_TYPE = "DATA_TYPE";
    public static final String CN_META_TYPE_NAME = "TYPE_NAME";
    public static final String CN_META_COLUMN_SIZE = "COLUMN_SIZE";
    public static final String CN_META_BUFFER_LENGTH = "BUFFER_LENGTH";
    public static final String CN_META_DECIMAL_DIGITS = "DECIMAL_DIGITS";
    public static final String CN_META_NUM_PREC_RADIX = "NUM_PREC_RADIX";
    public static final String CN_META_NULLABLE = "NULLABLE";
    public static final String CN_META_REMARKS = "REMARKS";
    public static final String CN_META_COLUMN_DEF = "COLUMN_DEF";
    public static final String CN_META_SQL_DATA_TYPE = "SQL_DATA_TYPE";
    public static final String CN_META_SQL_DATETIME_SUB = "SQL_DATETIME_SUB";
    public static final String CN_META_CHAR_OCTET_LENGTH = "CHAR_OCTET_LENGTH";
    public static final String CN_META_ORDINAL_POSITION = "ORDINAL_POSITION";
    public static final String CN_META_IS_NULLABLE = "IS_NULLABLE";
    public static final String CN_META_SCOPE_CATALOG = "SCOPE_CATALOG";
    public static final String CN_META_SCOPE_SCHEMA = "SCOPE_SCHEMA";
    public static final String CN_META_SCOPE_TABLE = "SCOPE_TABLE";
    public static final String CN_META_SOURCE_DATA_TYPE = "SOURCE_DATA_TYPE";
    public static final String CN_META_IS_AUTOINCREMENT = "IS_AUTOINCREMENT";
    public static final String CN_META_IS_GENERATEDCOLUMN = "IS_GENERATEDCOLUMN";
    public static final String CN_META_KEY_SEQ = "KEY_SEQ";
    public static final String CN_META_PK_NAME = "PK_NAME";
}
