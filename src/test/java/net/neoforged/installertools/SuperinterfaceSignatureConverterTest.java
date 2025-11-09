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
