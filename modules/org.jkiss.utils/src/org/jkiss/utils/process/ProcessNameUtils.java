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
package org.jkiss.utils.process;

import org.jkiss.code.NotNull;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.StandardConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProcessNameUtils {

    private static final String OS_WINDOWS = "win";
    private static final String OS_LINUX = "linux";
    private static final String OS_MAC = "mac";

    private ProcessNameUtils() {
    }

    @NotNull
    public static List<String> buildNamedCommand(@NotNull String processName, @NotNull List<String> command) {
        if (isWindows()) {
            return command;
        }

        String shell = isLinux() ? "/bin/bash" : "/bin/sh";
        List<String> shellCommand = new ArrayList<>();
        shellCommand.add(shell);
        shellCommand.add("-c");
        shellCommand.add(buildExecNamedCommand(processName, command));
        return shellCommand;
    }

    @NotNull
    private static String buildExecNamedCommand(@NotNull String processName, @NotNull List<String> command) {
        List<String> execCommand = new ArrayList<>();
        execCommand.add("exec");
        execCommand.add("-a");
        execCommand.add(quoteShellArg(processName));
        for (String arg : command) {
            execCommand.add(quoteShellArg(arg));
        }
        return String.join(" ", execCommand);
    }

    @NotNull
    private static String quoteShellArg(@NotNull String arg) {
        return CommonUtils.escapeBourneShellString(arg);
    }

    public static boolean isWindows() {
        return getOsName().contains(OS_WINDOWS);
    }

    public static boolean isLinux() {
        return getOsName().contains(OS_LINUX);
    }

    public static boolean isMac() {
        return getOsName().contains(OS_MAC);
    }

    @NotNull
    private static String getOsName() {
        return System.getProperty(StandardConstants.ENV_OS_NAME, "").toLowerCase(Locale.ENGLISH);
    }
}
