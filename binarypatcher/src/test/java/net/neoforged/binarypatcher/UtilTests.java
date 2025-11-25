package net.neoforged.binarypatcher;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UtilTests {

    @ParameterizedTest(name = "pattern=''{0}'', path=''{1}'', expected={2}")
    @CsvSource({
            // Exact matches
            "abc, abc, true",
            "abc, xyz, false",
            "/path/to/file, /path/to/file, true",

            // Single character wildcard (?)
            "a?c, abc, true",
            "a?c, ac, false",
            "a?c, abbc, false",
            "file?.txt, file1.txt, true",
            "file?.txt, file.txt, false",
            "file?.txt, file12.txt, false",
            "???, abc, true",
            "???, ab, false",

            // Single segment wildcard (*)
            "*.txt, file.txt, true",
            "*.txt, document.txt, true",
            "*.txt, file.doc, false",
            "a*c, abc, true",
            "a*c, ac, true",
            "a*c, abbbbc, true",
            "a*c, a/b/c, false",
            "file*, file.txt, true",
            "file*, file, true",
            "*file*, myfile.txt, true",

            // Multi-segment wildcard (**)
            "**/file.txt, file.txt, true",
            "**/file.txt, dir/file.txt, true",
            "**/file.txt, dir1/dir2/file.txt, true",
            "**/file.txt, dir1/dir2/dir3/file.txt, true",
            "**/file.txt, file.doc, false",
            "src/**/*.java, src/Main.java, true",
            "src/**/*.java, src/com/Main.java, true",
            "src/**/*.java, src/com/example/Main.java, true",
            "src/**/*.java, src/Main.txt, false",
            "**/test/**, a/test/b, true",
            "**/test/**, test/b/c, true",
            "**/test/**, a/b/test/c/d, true",

            // Leading/trailing wildcards
            "/**/data, /a/b/data, true",
            "data/**, data/a/b, true",

            // Combined wildcards
            "/api/*/user, /api/v1/user, true",
            "/api/*/user, /api/v2/user, true",
            "/api/*/user, /api/v1/v2/user, false",
            "/api/**/user, /api/user, true",
            "/api/**/user, /api/v1/user, true",
            "/api/**/user, /api/v1/v2/user, true",
            "*/?.txt, dir/a.txt, true",
            "*/?.txt, dir/ab.txt, false",

            // Edge cases
            "*, file, true",
            "*, path/file, false",
            "**, file, true",
            "**, path/file, true",
            "**, path/to/file, true",

            // Real-world patterns
            "**/*.class, target/classes/com/example/Main.class, true",
            "src/main/**/*.java, src/main/java/com/App.java, true",
            "static/**/*.css, static/css/style.css, true",
            "static/**/*.css, static/vendor/bootstrap/css/bootstrap.css, true",
            "/resources/**/config.xml, /resources/app/config.xml, true",
            "/resources/**/config.xml, /resources/config.xml, true"
    })
    void testSinglePattern(String pattern, String path, boolean expected) {
        assertEquals(expected, Util.createPathFilter(Collections.singleton(pattern)).test(path),
                String.format("Pattern '%s' should %smatch path '%s'",
                        pattern, expected ? "" : "not ", path));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideMultipleFilters")
    void testMultipleFilters(String description, String[] filters, String path, boolean expected) {
        Predicate<String> filter = Util.createPathFilter(Arrays.asList(filters));
        assertEquals(expected, filter.test(path),
                String.format("Filters %s should %smatch path '%s'",
                        java.util.Arrays.toString(filters), expected ? "" : "not ", path));
    }

    static Stream<Arguments> provideMultipleFilters() {
        return Stream.of(
                // File extensions
                Arguments.of("Multiple extensions match .txt",
                        new String[]{"*.txt", "*.java", "*.xml"}, "file.txt", true),
                Arguments.of("Multiple extensions match .java",
                        new String[]{"*.txt", "*.java", "*.xml"}, "Main.java", true),
                Arguments.of("Multiple extensions match .xml",
                        new String[]{"*.txt", "*.java", "*.xml"}, "config.xml", true),
                Arguments.of("Multiple extensions no match .pdf",
                        new String[]{"*.txt", "*.java", "*.xml"}, "file.pdf", false),

                // Directory patterns
                Arguments.of("Source directories match src",
                        new String[]{"src/**/*.java", "test/**/*.java"}, "src/com/Main.java", true),
                Arguments.of("Source directories match test",
                        new String[]{"src/**/*.java", "test/**/*.java"}, "test/AppTest.java", true),
                Arguments.of("Source directories no match lib",
                        new String[]{"src/**/*.java", "test/**/*.java"}, "lib/Util.java", false),

                // API routes
                Arguments.of("API routes match v1",
                        new String[]{"/api/v1/*", "/api/v2/*"}, "/api/v1/users", true),
                Arguments.of("API routes match v2",
                        new String[]{"/api/v1/*", "/api/v2/*"}, "/api/v2/products", true),
                Arguments.of("API routes no match v3",
                        new String[]{"/api/v1/*", "/api/v2/*"}, "/api/v3/orders", false),

                // Mixed patterns
                Arguments.of("Config files match yml",
                        new String[]{"**/*.yml", "**/*.yaml", "**/*.properties"}, "config/app.yml", true),
                Arguments.of("Config files match properties",
                        new String[]{"**/*.yml", "**/*.yaml", "**/*.properties"}, "application.properties", true),
                Arguments.of("Config files no match json",
                        new String[]{"**/*.yml", "**/*.yaml", "**/*.properties"}, "settings.json", false),

                // Complex nested patterns
                Arguments.of("Build outputs match classes",
                        new String[]{"target/**/*.class", "build/**/*.class"}, "target/classes/Main.class", true),
                Arguments.of("Build outputs match build",
                        new String[]{"target/**/*.class", "build/**/*.class"}, "build/com/App.class", true),
                Arguments.of("Build outputs no match src",
                        new String[]{"target/**/*.class", "build/**/*.class"}, "src/Main.class", false),

                // Edge cases
                Arguments.of("Empty path with wildcard",
                        new String[]{"*", "test/*"}, "", true),
                Arguments.of("Single filter via varargs",
                        new String[]{"*.txt"}, "file.txt", true),

                // Real-world scenarios
                Arguments.of("Static resources CSS",
                        new String[]{"static/**/*.css", "static/**/*.js", "static/**/*.png"},
                        "static/css/style.css", true),
                Arguments.of("Static resources JS",
                        new String[]{"static/**/*.css", "static/**/*.js", "static/**/*.png"},
                        "static/js/app.js", true),
                Arguments.of("Static resources PNG",
                        new String[]{"static/**/*.css", "static/**/*.js", "static/**/*.png"},
                        "static/images/logo.png", true),
                Arguments.of("Static resources no match HTML",
                        new String[]{"static/**/*.css", "static/**/*.js", "static/**/*.png"},
                        "static/index.html", false)
        );
    }

    @ParameterizedTest(name = "path=''{1}'' with patterns {0}")
    @MethodSource("provideMatchAnyTests")
    void testMatchAnyHelper(String[] patterns, String path, boolean expected) {
        Predicate<String> filter = Util.createPathFilter(Arrays.asList(patterns));

        assertEquals(expected, filter.test(path),
                String.format("matchAny should return %s for path '%s'", expected, path));
    }

    static Stream<Arguments> provideMatchAnyTests() {
        return Stream.of(
                Arguments.of(new String[]{"*.txt", "*.doc"}, "file.txt", true),
                Arguments.of(new String[]{"*.txt", "*.doc"}, "file.doc", true),
                Arguments.of(new String[]{"*.txt", "*.doc"}, "file.pdf", false),
                Arguments.of(new String[]{"src/**", "test/**"}, "src/Main.java", true),
                Arguments.of(new String[]{"src/**", "test/**"}, "test/AppTest.java", true),
                Arguments.of(new String[]{"src/**", "test/**"}, "lib/Util.java", false)
        );
    }

}
