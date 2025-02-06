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
package com.dbeaver.rpc.api;

import java.util.UUID;

public class RpcRequest {
    private final UUID messageId;
    private final String payload;

    public RpcRequest(UUID messageId, String payload) {
        this.messageId = messageId;
        this.payload = payload;
    }

    public UUID messageId() {
        return messageId;
    }

    public String payload() {
        return payload;
    }
}
