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

import org.jkiss.code.NotNull;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class ServerStatus {
    @NotNull
    private final String status;
    @NotNull
    private final LocalDateTime startTime;

    @NotNull
    private final Map<String, ServiceStatus> services = new LinkedHashMap<>();

    public ServerStatus(@NotNull String status, @NotNull LocalDateTime startTime) {
        this.startTime = startTime;
        this.status = status;
    }

    @NotNull
    public LocalDateTime getStartTime() {
        return startTime;
    }

    @NotNull
    public String getStatus() {
        return status;
    }

    public void addServiceStatus(@NotNull String serviceName, @NotNull ServiceStatus serviceStatus) {
        this.services.put(serviceName, serviceStatus);
    }
}
