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
package com.dbeaver.servlet.model;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class BaseHealthServlet extends HttpServlet {
    private static final LocalDateTime startTime = LocalDateTime.now();
    private static final String PARAM_EXTENDED_STATUS = "extendedStatus";
    protected static final String DB_CONNECTION_STATUS = "database-connection";
    protected static final String OK_STATUS = "ok";
    protected static final String NO_CONNECTION_STATUS = "no connection";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setHeader("Access-Control-Allow-Origin", "*");
        boolean extendedStatus = Boolean.parseBoolean(req.getParameter(PARAM_EXTENDED_STATUS));
        if (extendedStatus) {
            Map<String, String> status = new LinkedHashMap<>();
            fillExtendedData(status);
            try (Writer writer = response.getWriter()) {
                writer.write("{\n");
                int i = 0;
                for (Map.Entry<String, String> stringStringEntry : status.entrySet()) {
                    i++;
                    String key = stringStringEntry.getKey();
                    String value = stringStringEntry.getValue();
                    writer.write("    " + key + ": " + value);
                    if (i < status.size()) {
                        writer.write(",");
                    }
                    writer.write("\n");
                }
                writer.write("}");
            }
        } else {
            try (Writer writer = response.getWriter()) {
                writer.write(OK_STATUS);
            }
        }
    }

    protected void fillExtendedData(Map<String, String> extendedStatus) {
        extendedStatus.put("status", OK_STATUS);
        extendedStatus.put("start-time", startTime.toString());
    }
}
