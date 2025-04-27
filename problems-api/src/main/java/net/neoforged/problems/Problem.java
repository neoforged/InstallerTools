package net.neoforged.problems;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Inspired by https://docs.gradle.org/8.14/javadoc/org/gradle/api/problems/Problem.html
 */
public final class Problem {
    private final ProblemId problemId;
    private final ProblemSeverity severity;
    private final ProblemLocation location;
    @Nullable
    private final String contextualLabel;
    @Nullable
    private final String details;
    @Nullable
    private final String solution;
    @Nullable
    private final String documentedAt;

    private Problem(
            ProblemId problemId,
            ProblemSeverity severity,
            @Nullable ProblemLocation location,
            @Nullable String contextualLabel,
            @Nullable String details,
            @Nullable String solution,
            @Nullable String documentedAt
    ) {
        this.problemId = Objects.requireNonNull(problemId, "problemId");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.location = location;
        this.contextualLabel = contextualLabel;
        this.details = details;
        this.solution = solution;
        this.documentedAt = documentedAt;
    }

    public ProblemId problemId() {
        return problemId;
    }

    public ProblemSeverity severity() {
        return severity;
    }

    public @Nullable ProblemLocation location() {
        return location;
    }

    public @Nullable String contextualLabel() {
        return contextualLabel;
    }

    public @Nullable String details() {
        return details;
    }

    public @Nullable String solution() {
        return solution;
    }

    public @Nullable String documentedAt() {
        return documentedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Problem problem = (Problem) o;
        return Objects.equals(problemId, problem.problemId)
                && severity == problem.severity
                && Objects.equals(location, problem.location)
                && Objects.equals(contextualLabel, problem.contextualLabel)
                && Objects.equals(details, problem.details)
                && Objects.equals(solution, problem.solution)
                && Objects.equals(documentedAt, problem.documentedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(problemId, severity, location, contextualLabel, details, solution, documentedAt);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append('[').append(severity).append("] ").append(problemId);
        if (contextualLabel != null) {
            result.append(": ").append(contextualLabel);
        }
        if (location != null) {
            result.append(" @ ").append(location);
        }

        return result.toString();
    }

    public static Builder builder(ProblemId id) {
        return new Builder(id);
    }

    public static Builder builder(Problem problem) {
        return new Builder(problem);
    }

    /**
     * Inspired by https://docs.gradle.org/8.14/javadoc/org/gradle/api/problems/ProblemSpec.html
     */
    public static final class Builder {
        private final ProblemId problemId;
        private ProblemSeverity severity;
        private ProblemLocation location;
        @Nullable
        private String contextualLabel;
        @Nullable
        private String details;
        @Nullable
        private String solution;
        @Nullable
        private String documentedAt;

        private Builder(ProblemId problemId) {
            this.problemId = problemId;
            this.severity = ProblemSeverity.WARNING;
        }

        private Builder(Problem problem) {
            this.problemId = problem.problemId;
            this.severity = problem.severity;
            this.location = problem.location;
            this.contextualLabel = problem.contextualLabel;
            this.details = problem.details;
            this.solution = problem.solution;
            this.documentedAt = problem.documentedAt;
        }

        public Builder severity(ProblemSeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder location(@Nullable ProblemLocation location) {
            this.location = location;
            return this;
        }

        public Builder contextualLabel(@Nullable String contextualLabel) {
            this.contextualLabel = contextualLabel;
            return this;
        }

        public Builder details(@Nullable String details) {
            this.details = details;
            return this;
        }

        public Builder solution(@Nullable String solution) {
            this.solution = solution;
            return this;
        }

        public Builder documentedAt(@Nullable String documentedAt) {
            this.documentedAt = documentedAt;
            return this;
        }

        public Builder inFile(Path file) {
            return location(ProblemLocation.ofFile(file));
        }

        /**
         * @param line 1-based line number.
         */
        public Builder inFileOnLine(Path file, int line) {
            return location(ProblemLocation.ofLocationInFile(file, line));
        }

        /**
         * @param line   1-based line number.
         * @param column 1-based column number.
         */
        public Builder inFileOnLine(Path file, int line, int column) {
            return location(ProblemLocation.ofLocationInFile(file, line, column));
        }

        /**
         * @param line   1-based line number.
         * @param column 1-based column number.
         */
        public Builder inFileOnLine(Path file, int line, int column, int length) {
            return location(ProblemLocation.ofLocationInFile(file, line, column, length));
        }

        /**
         * @param offset 0-based byte offset into the file.
         */
        public Builder inFileAtOffset(Path file, int offset) {
            return location(ProblemLocation.ofOffsetInFile(file, offset));
        }

        /**
         * @param offset 0-based byte offset into the file.
         */
        public Builder inFileAtOffset(Path file, int offset, int length) {
            return location(ProblemLocation.ofOffsetInFile(file, offset, length));
        }

        public Problem build() {
            return new Problem(
                    problemId,
                    severity,
                    location,
                    contextualLabel,
                    details,
                    solution,
                    documentedAt
            );
        }
    }
}
