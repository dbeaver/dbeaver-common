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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommonUtilsComparisonTest {
    @ParameterizedTest
    @MethodSource("sameTypeNumbers")
    void comparesSameTypeNumbersUsingTheirNaturalOrder(@NotNull Number first, @NotNull Number second, int expected) {
        assertEquals(expected, Integer.signum(CommonUtils.compareNumbers(first, second)));
        assertEquals(-expected, Integer.signum(CommonUtils.compareNumbers(second, first)));
    }

    @NotNull
    private static Stream<Arguments> sameTypeNumbers() {
        return Stream.of(
            Arguments.of((byte) -1, (byte) 1, -1),
            Arguments.of(Short.MIN_VALUE, Short.MAX_VALUE, -1),
            Arguments.of(Integer.MIN_VALUE, Integer.MAX_VALUE, -1),
            Arguments.of(2030565120120958977L, 2030565120120958978L, -1),
            Arguments.of(2030565120120958978L, 2030565120120958979L, -1),
            Arguments.of(9007199254740992L, 9007199254740993L, -1),
            Arguments.of(Long.MIN_VALUE, Long.MIN_VALUE + 1, -1),
            Arguments.of(Long.MAX_VALUE - 1, Long.MAX_VALUE, -1),
            Arguments.of(Long.MIN_VALUE, Long.MAX_VALUE, -1),
            Arguments.of(-2030565120120958979L, -2030565120120958978L, -1),
            Arguments.of(2030565120120958977L, 2030565120120958977L, 0),
            Arguments.of(
                new BigInteger("123456789012345678901234567890"),
                new BigInteger("123456789012345678901234567891"),
                -1
            ),
            Arguments.of(new BigDecimal("1.00000000000000000001"), new BigDecimal("1.00000000000000000002"), -1),
            Arguments.of(new BigDecimal("1E+400"), new BigDecimal("2E+400"), -1),
            Arguments.of(new BigDecimal("1.0"), new BigDecimal("1.00"), 0),
            Arguments.of(1.25f, 1.5f, -1),
            Arguments.of(1.25d, 1.5d, -1),
            Arguments.of(-0.0f, 0.0f, -1),
            Arguments.of(-0.0d, 0.0d, -1),
            Arguments.of(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -1),
            Arguments.of(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -1),
            Arguments.of(Float.NaN, Float.POSITIVE_INFINITY, 1),
            Arguments.of(Double.NaN, Double.POSITIVE_INFINITY, 1),
            Arguments.of(Float.NaN, Float.NaN, 0),
            Arguments.of(Double.NaN, Double.NaN, 0)
        );
    }

    @Test
    void sortsAdjacentLargeIdsInBothDirections() {
        List<Long> ids = new ArrayList<>(List.of(2030565120120958978L, 2030565120120958977L, 2030565120120958979L));
        Comparator<Long> comparator = CommonUtils::compareNumbers;

        ids.sort(comparator);
        assertEquals(List.of(2030565120120958977L, 2030565120120958978L, 2030565120120958979L), ids);

        ids.sort(comparator.reversed());
        assertEquals(List.of(2030565120120958979L, 2030565120120958978L, 2030565120120958977L), ids);
    }

    @ParameterizedTest
    @MethodSource("fallbackNumbers")
    void preservesDoubleBasedFallback(@NotNull Number first, @NotNull Number second, int expected) {
        assertEquals(expected, Integer.signum(CommonUtils.compareNumbers(first, second)));
        assertEquals(-expected, Integer.signum(CommonUtils.compareNumbers(second, first)));
    }

    @NotNull
    private static Stream<Arguments> fallbackNumbers() {
        return Stream.of(
            Arguments.of(1, 2L, -1),
            Arguments.of(2, 2L, 0),
            Arguments.of(0.1f, 0.1d, 1),
            Arguments.of(new BigDecimal("1.5"), 1L, 1),
            Arguments.of(BigInteger.ONE, 2L, -1),
            Arguments.of(-0.0f, 0.0d, -1),
            Arguments.of(Double.NaN, 1L, 1),
            Arguments.of(new AtomicLong(1), new AtomicLong(2), -1)
        );
    }

    @Test
    void rejectsNullNumbers() {
        assertThrows(NullPointerException.class, () -> CommonUtils.compareNumbers(null, 1));
        assertThrows(NullPointerException.class, () -> CommonUtils.compareNumbers(1, null));
        assertThrows(NullPointerException.class, () -> CommonUtils.compareNumbers(null, null));
    }
}
