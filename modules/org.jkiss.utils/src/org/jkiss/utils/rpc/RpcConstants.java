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

import org.jkiss.utils.StandardConstants;

import java.text.SimpleDateFormat;

public final class RpcConstants {
    public static final SimpleDateFormat ISO_TIMESTAMP_FORMAT = new SimpleDateFormat(StandardConstants.ISO_TIMESTAMP_PATTERN);
    public static final SimpleDateFormat ISO_TIME_FORMAT = new SimpleDateFormat(StandardConstants.ISO_TIME_PATTERN);
    public static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat(StandardConstants.ISO_DATE_PATTERN);

    public static final String HANDSHAKE_ERROR_HEADER = "X-Handshake-Error";

    private RpcConstants() {
    }
}
