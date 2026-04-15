/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2026 DBeaver Corp
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jkiss.code.NotNull;
import org.jkiss.utils.HttpConstants;
import org.jkiss.utils.rest.RpcConstants;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;

public class BaseHealthServlet extends HttpServlet {
    private static final LocalDateTime startTime = LocalDateTime.now();
    private static final Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(LocalDateTime.class, new RpcConstants.LocalDateTimeAdapter())
        .create();
    private static final String PARAM_EXTENDED_STATUS = "extendedStatus";

    protected static final String OK_STATUS = "ok";
    protected static final String NO_CONNECTION_STATUS = "no connection";
    protected static final String DB_SERVICE = "database";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType(HttpConstants.CONTENT_TYPE_JSON);
        response.setHeader("Access-Control-Allow-Origin", "*");
        boolean extendedStatus = Boolean.parseBoolean(req.getParameter(PARAM_EXTENDED_STATUS));
        if (extendedStatus) {
            ServerStatus serverStatus = new ServerStatus(OK_STATUS, startTime);
            fillExtendedData(serverStatus);
            try (Writer writer = response.getWriter()) {
                writer.write(gson.toJson(serverStatus));
            }
        } else {
            try (Writer writer = response.getWriter()) {
                writer.write(OK_STATUS);
            }
        }
    }

    protected void fillExtendedData(@NotNull ServerStatus serverStatus) {
    }
}
