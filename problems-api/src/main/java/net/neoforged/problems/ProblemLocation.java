package net.neoforged.problems;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

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
        this.file = file;
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
     */
    public static ProblemLocation ofOffsetInFile(Path file, int offset) {
        return new ProblemLocation(file, null, null, offset, null);
    }

    /**
     * @param offset 0-based byte offset into the file.
     */
    public static ProblemLocation ofOffsetInFile(Path file, int offset, int length) {
        return new ProblemLocation(file, null, null, offset, length);
    }

    public Path file() {
        return file;
    }

    @Nullable
    public Integer line() {
        return line;
    }

    @Nullable
    public Integer column() {
        return column;
    }

    @Nullable
    public Integer offset() {
        return offset;
    }

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
        return "ProblemLocation[" +
                "file=" + file + ", " +
                "line=" + line + ", " +
                "column=" + column + ", " +
                "offset=" + offset + ", " +
                "length=" + length + ']';
    }

}
