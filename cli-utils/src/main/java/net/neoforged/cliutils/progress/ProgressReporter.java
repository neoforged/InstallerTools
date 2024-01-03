package net.neoforged.cliutils.progress;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URLConnection;
import java.text.DecimalFormat;

/**
 * A {@link ProgressManager} that forwards all changes to a {@link ProgressReporter#output print stream}
 * that writes the changes with an ANSI modifier {@value #MODIFIER_KEY}. <br>
 * The ANSI modifier takes the form "\033[{@literal progressmanager};action value". <p>
 * The {@link #getDefault() default reporter} will only be enabled if the {@value #ENABLED_PROPERTY} system property is set to {@code true},
 * and will output to {@link System#err}.
 * @see ProgressActionType for the actions
 */
public class ProgressReporter implements ProgressManager {
    public static final String MODIFIER_KEY = "progressmanager";
    public static final String ENABLED_PROPERTY = "net.neoforged.progressmanager.enabled";
    private static final DecimalFormat TWO_DECIMALS = new DecimalFormat("#.00");

    protected final boolean enabled;
    protected final PrintStream output;

    public ProgressReporter(boolean enabled, PrintStream output) {
        this.enabled = enabled;
        this.output = output;
    }

    public static ProgressReporter getDefault() {
        return new ProgressReporter(Boolean.getBoolean(ENABLED_PROPERTY), System.err);
    }

    @Override
    public void setMaxProgress(int maxProgress) {
        write(ProgressActionType.MAX_PROGRESS, String.valueOf(maxProgress));
    }

    @Override
    public void setProgress(int progress) {
        write(ProgressActionType.PROGRESS, String.valueOf(progress));
    }

    @Override
    public void setPercentageProgress(double progress) {
        write(ProgressActionType.PROGRESS, TWO_DECIMALS.format(progress) + "%");
    }

    @Override
    public void setStep(String name) {
        write(ProgressActionType.STEP, name);
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
        write(ProgressActionType.INDETERMINATE, String.valueOf(indeterminate));
    }

    protected void write(ProgressActionType type, String value) {
        if (!enabled) return;

        try {
            //noinspection RedundantStringFormatCall
            output.println(String.format("\033[%s;%s %s",  MODIFIER_KEY, type.identifier, value));
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
            System.err.println("Failed to write progress: " + exception);
        }
    }

    public InputStream wrapDownload(URLConnection connection) throws IOException {
        connection.connect();
        setMaxProgress(connection.getContentLength());
        return wrapDownload(connection.getInputStream());
    }

    public InputStream wrapDownload(InputStream in) {
        return new FilterInputStream(in) {
            private int nread = 0;

            @Override
            public int read() throws IOException {
                int c = in.read();
                if (c >= 0) setProgress(++nread);
                return c;
            }


            @Override
            public int read(byte[] b) throws IOException {
                int nr = in.read(b);
                if (nr > 0) setProgress(nread += nr);
                return nr;
            }


            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int nr = in.read(b, off, len);
                if (nr > 0) setProgress(nread += nr);
                return nr;
            }


            @Override
            public long skip(long n) throws IOException {
                long nr = in.skip(n);
                if (nr > 0) setProgress(nread += nr);
                return nr;
            }
        };
    }
}
