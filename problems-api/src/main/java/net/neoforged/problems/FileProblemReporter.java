package net.neoforged.problems;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileProblemReporter implements ProblemReporter, AutoCloseable {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeHierarchyAdapter(Path.class, new TypeAdapter<Path>() {
                @Override
                public void write(JsonWriter out, Path value) throws IOException {
                    out.value(value.toAbsolutePath().toString());
                }

                @Override
                public Path read(JsonReader in) throws IOException {
                    return Paths.get(in.nextString());
                }
            })
            .create();

    private final Path problemsReport;

    private final List<Problem> problems = new ArrayList<>();

    public FileProblemReporter(Path problemsReport) {
        this.problemsReport = problemsReport;
    }

    @Override
    public synchronized void report(Problem problem) {
        problems.add(problem);
    }

    @Override
    public void close() throws IOException {
        try (Writer writer = Files.newBufferedWriter(problemsReport, StandardCharsets.UTF_8)) {
            GSON.toJson(problems, writer);
        }
    }

    @VisibleForTesting
    public static List<Problem> loadRecords(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return Arrays.asList(GSON.fromJson(reader, Problem[].class));
        }
    }
}
