/*
 * InstallerTools
 * Copyright (c) 2019-2025.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.neoforged.installertools;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SuperinterfaceSignatureConverterTest {
    // See https://github.com/neoforged/JavaSourceTransformer?tab=readme-ov-file#interface-injection for the spec
    @ParameterizedTest
    @CsvSource({
        "java/io/Serializable,Ljava/io/Serializable;",
        "java/util/List<java.lang.String>,Ljava/util/List<Ljava/lang/String;>;",
        "java/util/List<my.ClassName$Inner<T>>,Ljava/util/List<Lmy/ClassName$Inner<TT;>;>;",
        "com/example/MyInterface<T>,Lcom/example/MyInterface<TT;>;",
        "com/example/MyInterface<other.Interface<T>>,Lcom/example/MyInterface<Lother/Interface<TT;>;>;",
        "java/lang/Runnable,Ljava/lang/Runnable;",
        "'java/util/Map$Entry<K,V>','Ljava/util/Map$Entry<TK;TV;>;'",
        "pkg/Test<java.util.List<? extends java.lang.Number>>,Lpkg/Test<Ljava/util/List<+Ljava/lang/Number;>;>;",
    })
    void testSingleSuperinterfaceConversion(String input, String expectedSignature) {
        String actual = SuperinterfaceSignatureConverter.convert("Ljava/lang/Object;", java.util.Collections.singletonList(input));
        actual = actual.substring("Ljava/lang/Object;".length()); // Remove the "Ljava/lang/Object;" prefix)
        assertEquals(expectedSignature, actual);
    }
}
