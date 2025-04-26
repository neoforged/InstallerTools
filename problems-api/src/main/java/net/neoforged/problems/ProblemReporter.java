package net.neoforged.problems;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Report problems from plugins.
 */
public interface ProblemReporter {
    ProblemReporter NOOP = problem -> {
    };

    default void report(ProblemId problemId, ProblemSeverity severity, ProblemLocation location, String contextualLabel) {
        report(Problem.builder(problemId).severity(severity).location(location).contextualLabel(contextualLabel).build());
    }

    /**
     * Reports a location independent problem.
     */
    default void report(ProblemId problemId, ProblemSeverity severity, String contextualLabel) {
        report(Problem.builder(problemId).severity(severity).contextualLabel(contextualLabel).build());
    }

    void report(Problem problem);

    /**
     * Loads problems from the given report file (see {@link FileProblemReporter}) and add any that pass the given
     * filter to this reporter. If the given file is missing, nothing happens.
     */
    default void tryMergeFromFile(Path reportFile, Predicate<Problem> filter) throws IOException {
        try {
            for (Problem problem : FileProblemReporter.loadRecords(reportFile)) {
                if (filter.test(problem)) {
                    report(problem);
                }
            }
        } catch (NoSuchFileException ignored) {
        }
    }
}
