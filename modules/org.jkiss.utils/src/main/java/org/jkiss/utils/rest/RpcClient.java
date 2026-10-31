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

import org.jkiss.code.NotNull;

import java.lang.reflect.Proxy;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class RpcClient {

    private static final Logger log = Logger.getLogger(RpcClient.class.getName());

    protected RpcClient() {
        // prevents instantiation
    }

    /**
     * Releases a client's transport resources without requiring the service interface to extend {@link AutoCloseable}.
     * Waits for an ongoing invocation to finish. After successful closure, remote calls will fail with {@link RpcException}.
     * Invalid clients and runtime failures during cleanup are logged rather than thrown.
     */
    public static void close(@NotNull Object client) {
        try {
            var handler = Proxy.getInvocationHandler(client);
            if (handler instanceof RpcInvocationHandler rpcHandler) {
                synchronized (rpcHandler) {
                    rpcHandler.closeClient();
                }
            } else {
                log.warning("Cannot close client: not an RPC client");
            }
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Error closing RPC client", e);
        }
    }

    @NotNull
    protected static <T> T createProxy(
        @NotNull Class<T> cls,
        @NotNull RpcInvocationHandler invocationHandler
    ) {
        final Object proxy = Proxy.newProxyInstance(
            cls.getClassLoader(),
            new Class[] {cls, RestProxy.class},
            invocationHandler
        );

        return cls.cast(proxy);
    }

}
