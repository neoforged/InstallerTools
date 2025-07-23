package net.neoforged.jartransform;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * A framework to process entries in a ZIP file in parallel and optionally transform them or copy them
 * unmodified to a destination file.
 */
public final class ParallelZipProcessor {
    private ParallelZipProcessor() {
    }

    public static void process(File inputFile,
                               File outputFile,
                               ZipProcessingStrategy strategy
    ) throws IOException {

        try (ZipInput input = new ZipInput(inputFile);
             ZipOutput output = new ZipOutput(outputFile)) {

            List<CompletableFuture<ZipProcessingStrategy.TransformedContent>> contentFutures = new ArrayList<>(1000);

            contentFutures.addAll(strategy.getLeadingContent(input));

            Iterator<ZipTransformEntry> entries = input.getEntries();
            while (entries.hasNext()) {
                ZipTransformEntry entry = entries.next();
                CompletableFuture<ZipProcessingStrategy.TransformedContent> future = strategy.transformContent(input, entry);
                if (future != null) {
                    contentFutures.add(future);
                }
            }

            contentFutures.addAll(strategy.getTrailingContent(input));

            for (CompletableFuture<ZipProcessingStrategy.TransformedContent> contentFuture : contentFutures) {
                ZipProcessingStrategy.TransformedContent content;
                try {
                    content = contentFuture.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting on an off-thread transformation.", e);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    }
                    throw new IOException("An off-thread transform failed.", e.getCause());
                }

                output.addRawArchiveEntry(content.entry, new ByteArrayInputStream(content.rawContent));
            }
        }

    }
}

