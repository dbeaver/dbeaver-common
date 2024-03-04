/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
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

import org.jkiss.code.NotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Represents a reference-counting wrapper for an object.
 *
 * @param <T> type of the object to be wrapped
 * @see #retain()
 * @see #release()
 * @see #get()
 */
public class Rc<T> {
    private final AtomicInteger counter = new AtomicInteger(1);
    private final Consumer<T> cleaner;
    private T value;

    public Rc(@NotNull T value, @NotNull Consumer<T> cleaner) {
        this.value = value;
        this.cleaner = cleaner;
    }

    /**
     * Returns the wrapped object.
     *
     * @throws IllegalStateException if the object is already disposed
     */
    @NotNull
    public T get() {
        if (counter.get() <= 0) {
            throw new IllegalStateException("Object is already disposed");
        }
        return value;
    }

    /**
     * Increments the reference counter.
     *
     * @return this object
     * @throws IllegalStateException if the object is already disposed
     */
    @NotNull
    public Rc<T> retain() {
        final int refs = counter.incrementAndGet();

        if (refs <= 0) {
            counter.decrementAndGet();
            throw new IllegalStateException("Object is already disposed");
        }

        return this;
    }

    /**
     * Decrements the reference counter.
     * <p>
     * If the counter reaches zero, the wrapped object is disposed.
     *
     * @throws IllegalStateException if the object is already disposed
     */
    public void release() {
        final int refs = counter.decrementAndGet();

        if (refs == 0) {
            cleaner.accept(value);
            value = null;
        } else if (refs < 0) {
            throw new IllegalStateException();
        }
    }

    public int getCount() {
        return counter.get();
    }

    @Override
    public String toString() {
        return "Rc[refs=" + counter.get() + ", value=" + value + ']';
    }
}
