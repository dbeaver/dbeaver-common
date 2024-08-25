/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp
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
package org.jkiss.utils.rest;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.utils.BeanUtils;
import org.jkiss.utils.CommonUtils;

import java.lang.reflect.*;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class RpcInvocationHandler implements InvocationHandler, RestProxy {
    private static final Pattern ST_LINE_PATTERN = Pattern.compile("\\s*at\\s+([\\w/.$]+)\\((.+)\\)");

    @NotNull
    private final Class<?> clientClass;
    protected final URI uri;
    protected final Gson gson;
    protected final String userAgent;
    protected final ThreadLocal<Type> resultType = new ThreadLocal<>();

    protected RpcInvocationHandler(
        @NotNull Class<?> clientClass,
        @NotNull URI uri,
        @NotNull Gson gson,
        @NotNull String userAgent
    ) {
        this.clientClass = clientClass;
        this.uri = uri;
        this.gson = gson;
        this.userAgent = userAgent;
    }

    @Override
    public synchronized Object invoke(Object proxy, Method method, Object[] args) throws RpcException {
        // Client-side API
        Class<?> declaringClass = method.getDeclaringClass();
        if (declaringClass == Object.class) {
            return BeanUtils.handleObjectMethod(proxy, method, args);
        } else if (declaringClass == RestProxy.class) {
            setNextCallResultType((Type) args[0]);
            return null;
        } else if (method.getName().equals("close") && (declaringClass == AutoCloseable.class || declaringClass == clientClass)) {
            closeClient();
            return null;
        }
        if (isClientClosed()) {
            throw new RpcException("Rest client has been terminated");
        }

        // Call remote
        final RequestMapping mapping = method.getDeclaredAnnotation(RequestMapping.class);

        final Parameter[] parameters = method.getParameters();
        final Map<String, JsonElement> values = new LinkedHashMap<>(parameters.length);

        for (int i = 0; i < parameters.length; i++) {
            final Parameter p = parameters[i];
            final RequestParameter param = p.getDeclaredAnnotation(RequestParameter.class);

            String paramName = param == null ? p.getName() : param.value();
            if (CommonUtils.isEmptyTrimmed(paramName)) {
                throw createException(method, "one or more of parameters has empty name (it can be specified in @RequestParameter)");
            }
            JsonElement argument;
            try {
                argument = gson.toJsonTree(args[i]);
            } catch (Throwable e) {
                throw new RpcException("Failed to serialize argument " + i + ": " + e.getMessage(), e);
            }
            if (values.put(paramName, argument) != null) {
                throw createException(method, "one or more of its parameters share the same name specified in @RequestParameter");
            }
        }

        try {
            String contents = invokeRemoteMethod(method, mapping, parameters, values);

            Type returnType = resultType.get();
            if (returnType == null) {
                returnType = method.getGenericReturnType();
            } else {
                resultType.remove();
            }
            if (returnType == void.class) {
                return null;
            }
            if (returnType instanceof TypeVariable) {
                Type[] bounds = ((TypeVariable<?>) returnType).getBounds();
                if (bounds.length > 0) {
                    returnType = bounds[0];
                }
            }
            if (returnType instanceof ParameterizedType && ((ParameterizedType) returnType).getRawType() == Class.class) {
                // Convert to raw class type to force our serializer to work
                returnType = Class.class;
            }

            try {
                return gson.fromJson(contents, returnType);
            } catch (Throwable e) {
                //just debug breakpoint, rethrow it
                throw e;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RpcException(e);
        }
    }

    protected abstract boolean isClientClosed();

    protected abstract String invokeRemoteMethod(
        @NotNull Method method,
        @Nullable RequestMapping mapping,
        @NotNull Parameter[] parameters,
        @NotNull Map<String, JsonElement> values);

    protected abstract void closeClient();

    @NotNull
    private static RpcException createException(@NotNull Method method, @NotNull String reason) {
        return new RpcException("Unable to invoke the method " + method + " because " + reason);
    }

    @Override
    public void setNextCallResultType(Type type) {
        this.resultType.set(type);
    }

    protected static void handleRpcError(String contents) throws RpcException {
        if (contents.startsWith("<")) {
            // Seems to be html error page
//            Matcher matcher = Pattern.compile("<title>(.+)</title>").matcher(contents);
//            if (matcher.find()) {
//                throw new RestException("Server error: " + matcher.group(1));
//            }
            throw new RpcException(contents);
        }
        String[] stackTraceRows = contents.split("\n");
        String errorLine = stackTraceRows[0];
        List<StackTraceElement> stackTraceElements = new ArrayList<>();
        for (int i = 1; i < stackTraceRows.length; i++) {
            Matcher matcher = ST_LINE_PATTERN.matcher(stackTraceRows[i]);
            if (matcher.find()) {
                String methodRef = matcher.group(1);
                int divPos = methodRef.lastIndexOf('.');
                String className = methodRef.substring(0, divPos);
                String methodName = methodRef.substring(divPos + 1);

                String classRef = matcher.group(2);
                divPos = classRef.indexOf(':');
                String fileName;
                int fileLine;
                if (divPos == -1) {
                    fileName = classRef;
                    fileLine = -1;
                } else {
                    fileName = classRef.substring(0, divPos).trim();
                    fileLine = CommonUtils.toInt(classRef.substring(divPos + 1).trim());
                }
                stackTraceElements.add(
                    new StackTraceElement(className, methodName, fileName, fileLine));
            }
        }
        RpcException runtimeException = new RpcException(errorLine);
        Collections.addAll(stackTraceElements, runtimeException.getStackTrace());
        runtimeException.setStackTrace(stackTraceElements.toArray(new StackTraceElement[0]));

        throw runtimeException;
    }

}
