package net.neoforged.cliutils.progress;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Intercepts {@link ProgressActionType} progress manager actions and redirects them to the {@code manager}.
 */
public class ProgressInterceptor extends FilterOutputStream {
    public static final String FULL_MODIFIER = "\033[" + ProgressReporter.MODIFIER_KEY + ";";

    protected final ProgressManager manager;
    public ProgressInterceptor(OutputStream out, ProgressManager manager) {
        super(out);
        this.manager = manager;
    }

    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
        final String text = new String(Arrays.copyOfRange(buf, off, len), StandardCharsets.UTF_8);
        final String[] lines = text.split("\r\n|\n");
        for (final String line : lines) {
            if (line.startsWith("\033[" + ProgressReporter.MODIFIER_KEY + ";")) {
                final String[] values = line.substring(FULL_MODIFIER.length()).split(" ", 2);
                // If not 2, the value's empty, which isn't supported, so ignore
                if (values.length == 2) {
                    final String first = values[0];
                    if (first.length() != 1) {
                        System.err.println("Invalid progress modifier: " + values[0]);
                        continue;
                    }

                    final ProgressActionType action = ProgressActionType.TYPES.get(first.charAt(0));
                    if (action == null) {
                        System.err.println("Unknown progress modifier: " + first.charAt(0));
                        continue;
                    }

                    action.acceptor.accept(manager, values[1]);
                }
            } else {
                out.write(line.getBytes(StandardCharsets.UTF_8));
                out.write('\n');
            }
        }
    }
}
