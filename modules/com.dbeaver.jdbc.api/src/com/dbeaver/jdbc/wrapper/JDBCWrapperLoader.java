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
package com.dbeaver.jdbc.wrapper;

import org.jkiss.code.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/** Loads typed adapters against the selected driver's classes, including its static registries. */
public final class JDBCWrapperLoader {
    private JDBCWrapperLoader() {
    }

    /**
     * Tries implementations in preference order. No global cache is retained: the caller may retain
     * the result for the lifetime of its driver, without preventing driver class loader disposal.
     * Reflection is limited to constructing our own adapters; native API calls are compiled normally.
     */
    @NotNull
    public static <T extends JDBCDriverWrapper> T load(
        @NotNull Class<T> api,
        @NotNull ClassLoader driverLoader,
        @NotNull JDBCWrapperDescriptor... implementations
    ) throws SQLException {
        SQLException failure = new SQLFeatureNotSupportedException("No compatible native JDBC wrapper for " + api.getName());
        for (JDBCWrapperDescriptor descriptor : implementations) {
            try {
                ClassLoader loader = new WrapperClassLoader(api, driverLoader, descriptor);
                T wrapper = api.cast(loader.loadClass(descriptor.implementationClass()).getConstructor().newInstance());
                wrapper.checkCompatibility();
                return wrapper;
            } catch (InvocationTargetException e) {
                failure.addSuppressed(new SQLException("Cannot initialize " + descriptor.implementationClass(), e.getCause()));
            } catch (ReflectiveOperationException | LinkageError | ClassCastException | SQLException e) {
                failure.addSuppressed(new SQLException("Cannot use " + descriptor.implementationClass(), e));
            }
        }
        throw failure;
    }

    private static final class WrapperClassLoader extends ClassLoader {
        private final Class<?> api;
        private final JDBCWrapperDescriptor descriptor;
        private final String implementationPackage;

        private WrapperClassLoader(
            @NotNull Class<?> api,
            @NotNull ClassLoader driverLoader,
            @NotNull JDBCWrapperDescriptor descriptor
        ) {
            super(driverLoader);
            this.api = api;
            this.descriptor = descriptor;
            String name = descriptor.implementationClass();
            implementationPackage = name.substring(0, name.lastIndexOf('.') + 1);
            if ((api.getPackageName() + '.').startsWith(implementationPackage)) {
                throw new IllegalArgumentException("Wrapper implementation and API must be in separate packages");
            }
        }

        @NotNull
        @Override
        protected Class<?> loadClass(@NotNull String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    if (name.startsWith(implementationPackage)) {
                        type = findClass(name);
                    } else if (name.startsWith(api.getPackageName() + '.')) {
                        type = api.getClassLoader().loadClass(name);
                    } else if (name.startsWith(JDBCDriverWrapper.class.getPackageName() + '.')) {
                        type = JDBCDriverWrapper.class.getClassLoader().loadClass(name);
                    } else {
                        // Never fall back to the wrapper bundle for native types. Doing so could
                        // silently bind a different driver version or a different static registry.
                        type = getParent().loadClass(name);
                    }
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }

        @NotNull
        @Override
        protected Class<?> findClass(@NotNull String name) throws ClassNotFoundException {
            String resource = '/' + name.replace('.', '/') + ".class";
            try (InputStream input = descriptor.source().getResourceAsStream(resource)) {
                if (input == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = input.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length, descriptor.source().getProtectionDomain());
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }
}
