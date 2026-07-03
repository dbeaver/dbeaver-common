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
package org.jkiss.utils;

import org.jkiss.code.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uses mutable ordered maps.
 * Main difference with Map.of() is order (LinkedHashMap) and mutability.
 */
public final class MapUtils {

    public static <K, V> Map<K, V> of() {
        return Map.of();
    }

    @NotNull
    public static <K, V> Map<K, V> of(K k1, V v1) {
        Map<K, V> lhm = new LinkedHashMap<>(1);
        lhm.put(k1, v1);
        return lhm;
    }

    @NotNull
    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2) {
        return createMap(
            new Object[] { k1, k2 },
            new Object[] { v1, v2 }
        );
    }

    @NotNull
    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
        return createMap(
            new Object[] { k1, k2, k3 },
            new Object[] { v1, v2, v3 }
        );
    }

    @NotNull
    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        return createMap(
            new Object[] { k1, k2, k3, k4 },
            new Object[] { v1, v2, v3, v4 }
        );
    }

    @NotNull
    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        return createMap(
            new Object[] { k1, k2, k3, k4, k5 },
            new Object[] { v1, v2, v3, v4, v5 }
        );
    }

    @NotNull
    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                               K k6, V v6) {
        return createMap(
            new Object[] { k1, k2, k3, k4, k5, k6 },
            new Object[] { v1, v2, v3, v4, v5, v6 }
        );
    }

    @NotNull
    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                               K k6, V v6, K k7, V v7) {
        return createMap(
            new Object[] { k1, k2, k3, k4, k5, k6, k7 },
            new Object[] { v1, v2, v3, v4, v5, v6, v7 }
        );
    }

    @NotNull
    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                               K k6, V v6, K k7, V v7, K k8, V v8) {
        return createMap(
            new Object[] { k1, k2, k3, k4, k5, k6, k7, k8 },
            new Object[] { v1, v2, v3, v4, v5, v6, v7, v8 }
        );
    }

    @NotNull
    public static <K, V> Map<K, V>createMap(@NotNull Object[] keys, @NotNull Object[] values) {
        if (keys.length != values.length) {
            throw new IllegalArgumentException("KEys length (" + keys.length + ") not equals to values length (" + values.length + ")");
        }
        Map<K, V> lhm = new LinkedHashMap<>(keys.length);
        for (int i = 0; i < keys.length; i++) {
            lhm.put((K) keys[i], (V) values[i]);
        }
        return lhm;
    }
}
