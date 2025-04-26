package net.neoforged.problems;

import java.util.Objects;

public final class Problem {
    private final ProblemId problemId;
    private final ProblemSeverity severity;
    private final ProblemLocation location;
    private final String message;

    public Problem(
            ProblemId problemId,
            ProblemSeverity severity,
            ProblemLocation location,
            String message
    ) {
        this.problemId = problemId;
        this.severity = severity;
        this.location = location;
        this.message = message;
    }

    public ProblemId problemId() {
        return problemId;
    }

    public ProblemSeverity severity() {
        return severity;
    }

    public ProblemLocation location() {
        return location;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        Problem that = (Problem) obj;
        return Objects.equals(this.problemId, that.problemId) &&
                Objects.equals(this.severity, that.severity) &&
                Objects.equals(this.location, that.location) &&
                Objects.equals(this.message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(problemId, severity, location, message);
    }

    @Override
    public String toString() {
        return "Problem[" +
                "problemId=" + problemId + ", " +
                "severity=" + severity + ", " +
                "location=" + location + ", " +
                "message=" + message + ']';
    }

}
