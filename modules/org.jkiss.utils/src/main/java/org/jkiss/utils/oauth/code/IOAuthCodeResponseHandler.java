/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2026 DBeaver Corp and others
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
package org.jkiss.utils.oauth.code;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Future;

public interface IOAuthCodeResponseHandler extends Closeable {

    void initServer() throws IOException;

    /** The bound loopback URI, when this handler owns a local callback server. */
    @Nullable
    default URI getCallbackUri() throws IOException {
        return null;
    }

    @NotNull
    Future<String> requestCode();

    void addStabContext();
}
