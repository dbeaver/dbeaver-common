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
package com.dbeaver.jdbc.wrapper.test;

import com.dbeaver.jdbc.wrapper.JDBCDriverWrapper;
import com.dbeaver.jdbc.wrapper.JDBCWrapperDescriptor;
import com.dbeaver.jdbc.wrapper.JDBCWrapperLoader;
import org.jkiss.code.NotNull;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import static org.junit.jupiter.api.Assertions.*;

class JDBCWrapperLoaderTest {
    @Test
    void sharesApiWithApplicationButDefinesImplementationSeparately() throws Exception {
        JDBCDriverWrapper wrapper = JDBCWrapperLoader.load(JDBCDriverWrapper.class, ClassLoader.getPlatformClassLoader(),
            descriptor("CompatibleWrapper"));
        assertNotSame(getClass().getClassLoader(), wrapper.getClass().getClassLoader());
        assertSame(ClassLoader.getPlatformClassLoader(), wrapper.getClass().getClassLoader().getParent());
        wrapper.checkCompatibility();
    }

    @Test
    void selectsNextVersionAfterIncompatibleOrUnlinkableImplementation() throws Exception {
        JDBCDriverWrapper wrapper = JDBCWrapperLoader.load(JDBCDriverWrapper.class, ClassLoader.getPlatformClassLoader(),
            descriptor("IncompatibleWrapper"), descriptor("UnlinkableWrapper"), descriptor("CompatibleWrapper"));
        assertEquals("CompatibleWrapper", wrapper.getClass().getSimpleName());
    }

    @Test
    void reportsAllFailuresWithTheirCauses() {
        SQLException error = assertThrows(SQLFeatureNotSupportedException.class,
            () -> JDBCWrapperLoader.load(JDBCDriverWrapper.class, ClassLoader.getPlatformClassLoader(),
                descriptor("IncompatibleWrapper"), descriptor("UnlinkableWrapper"), descriptor("MissingWrapper")));
        assertEquals(3, error.getSuppressed().length);
        assertInstanceOf(SQLFeatureNotSupportedException.class, error.getSuppressed()[0].getCause());
        assertInstanceOf(NoClassDefFoundError.class, error.getSuppressed()[1].getCause());
        assertInstanceOf(ClassNotFoundException.class, error.getSuppressed()[2].getCause());
    }

    @NotNull
    private static JDBCWrapperDescriptor descriptor(@NotNull String name) {
        return new JDBCWrapperDescriptor(JDBCWrapperLoaderTest.class, "com.dbeaver.jdbc.wrapper.fixture." + name);
    }
}
