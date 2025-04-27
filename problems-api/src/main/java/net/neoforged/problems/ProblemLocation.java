package net.neoforged.problems;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Identifies the location of a problem in a file.
 * <p>
 * Inspired by https://docs.gradle.org/8.14/javadoc/org/gradle/api/problems/ProblemLocation.html
 */
public final class ProblemLocation {
    private final Path file;
    @Nullable
    private final Integer line;
    @Nullable
    private final Integer column;
    @Nullable
    private final Integer offset;
    @Nullable
    private final Integer length;

    private ProblemLocation(Path file, @Nullable Integer line, @Nullable Integer column,
                            @Nullable Integer offset, @Nullable Integer length) {
        this.file = Objects.requireNonNull(file, "file");
        if (line != null && line <= 0) {
            throw new IllegalArgumentException("Line for problem location must be 1 or higher: " + line);
        }
        if (column != null && column <= 0) {
            throw new IllegalArgumentException("Column for problem location must be 1 or higher: " + column);
        }
        if (offset != null) {
            if (length == null) {
                throw new IllegalArgumentException("When an offset is given, a length must also be given.");
            }
            if (offset < 0) {
                throw new IllegalArgumentException("Byte-Offset for problem location must not be negative: " + offset);
            }
        }
        if (length != null && length < 0) {
            throw new IllegalArgumentException("Length for problem location must not be negative: " + length);
        }
        this.line = line;
        this.column = column;
        this.offset = offset;
        this.length = length;
    }

    public static ProblemLocation ofFile(Path file) {
        return new ProblemLocation(file, null, null, null, null);
    }

    /**
     * @param line 1-based line number.
     */
    public static ProblemLocation ofLocationInFile(Path file, int line) {
        return new ProblemLocation(file, line, null, null, null);
    }

    /**
     * @param line   1-based line number.
     * @param column 1-based column number.
     */
    public static ProblemLocation ofLocationInFile(Path file, int line, int column) {
        return new ProblemLocation(file, line, column, null, null);
    }

    /**
     * @param line   1-based line number.
     * @param column 1-based column number.
     */
    public static ProblemLocation ofLocationInFile(Path file, int line, int column, int length) {
        return new ProblemLocation(file, line, column, null, length);
    }

    /**
     * @param offset 0-based byte offset into the file.
     * @param length Number of bytes starting at {@code offset} that are relevant to the problem. May be 0.
     */
    public static ProblemLocation ofOffsetInFile(Path file, int offset, int length) {
        return new ProblemLocation(file, null, null, offset, length);
    }

    /**
     * Copies this location and replaces the file path found in the copy.
     */
    public ProblemLocation withFile(Path file) {
        return new ProblemLocation(file, line, column, offset, length);
    }

    public Path file() {
        return file;
    }

    /**
     * @return 1-based line offset into the {@link #file()}.
     * <p>
     * Mutually exclusive with {@link #offset()}.
     */
    @Nullable
    public Integer line() {
        return line;
    }

    /**
     * @return 1-based character offset into the line specified by {@link #line()}.
     */
    @Nullable
    public Integer column() {
        return column;
    }

    /**
     * 0-based byte-offset in {@link #file()}.
     * <p>
     * Mutually exclusive with {@link #line()} and {@link #column()}.
     */
    @Nullable
    public Integer offset() {
        return offset;
    }

    /**
     * Optionally specifies the length in bytes (for {@link #offset()}) or
     * characters (for {@link #column()}) of the problems' location.
     */
    @Nullable
    public Integer length() {
        return length;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        ProblemLocation that = (ProblemLocation) obj;
        return Objects.equals(this.file, that.file) &&
                Objects.equals(this.line, that.line) &&
                Objects.equals(this.column, that.column) &&
                Objects.equals(this.offset, that.offset) &&
                Objects.equals(this.length, that.length);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, line, column, offset, length);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(file);

        if (line != null) {
            result.append(':').append(line);
            if (column != null) {
                result.append(':').append(column);
                if (length != null) {
                    result.append('-').append(column + length);
                }
            }
        } else if (offset != null) {
            result.append(':').append(offset).append('b');
            if (length != null) {
                result.append('-').append(offset + length).append('b');
            }
        }

        return result.toString();
    }

}
