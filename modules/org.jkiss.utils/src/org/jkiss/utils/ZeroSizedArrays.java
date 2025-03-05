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
package org.jkiss.utils;

import java.sql.DriverPropertyInfo;

/**
 * A utils class with arrays with lengths of zero.
 * Since array lengths in Java are non-modifiable, it is almost always possible to share zero-length arrays,
 * rather than repeatedly allocate new ones. Such sharing may provide useful optimizations in the program runtime or footprint.
 */
public final class ZeroSizedArrays {
    /**
     * A zero-sized array of java.lang.Object
     */
    public final static Object[] OF_OBJECT = new Object[0];

    /**
     * A zero-sized array of java.lang.Class
     */
    @SuppressWarnings("rawtypes")
    public final static Class[] OF_CLASS = new Class[0];

    /**
     * A zero-sized array of java.lang.String
     */
    public static final String[] OF_STRING = new String[0];

    /**
     * A zero-sized array of primitive bytes
     */
    public static final byte[] OF_BYTE = new byte[0];

    /**
     * A zero-sized array of primitive chars
     */
    public static final char[] OF_CHAR = new char[0];

    /**
     * A zero-sized array of primitive integers
     */
    public static final int[] OF_INT = new int[0];

    /**
     * A zero-sized array of java.lang.Throwable
     */
    public static final Throwable[] OF_THROWABLE = new Throwable[0];

    /**
     * A zero-sized array of java.sql.DriverPropertyInfo
     */
    public static final DriverPropertyInfo[] OF_DRIVER_PROPERTY_INFO = new DriverPropertyInfo[0];

    private ZeroSizedArrays() {
        // Utility class
    }
}
