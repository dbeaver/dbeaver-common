/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
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
package org.jkiss.utils.rpc;

import java.util.UUID;

public class RpcRequest {
    private final UUID messageId;
    private final String payload;

    public RpcRequest(UUID messageId, String payload) {
        this.messageId = messageId;
        this.payload = payload;
    }

    /**
     * Returns the unique identifier of the message. This identifier is used to match responses to requests.
     */
    public UUID messageId() {
        return messageId;
    }

    /**
     * Returns the payload of the message.
     */
    public String payload() {
        return payload;
    }
}
