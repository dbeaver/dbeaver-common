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
package org.jkiss.utils.rest;

import com.google.gson.Gson;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.net.InetSocketAddress;
import java.util.function.Predicate;

@FunctionalInterface
public interface RequestHandlerFactory {
    @NotNull
    <T> RestServer.RequestHandler<T> createHandler(
        @NotNull Class<T> cls,
        @NotNull T object,
        @NotNull Gson gson,
        @NotNull Predicate<InetSocketAddress> filter,
        @Nullable String landingPage
    );
}
