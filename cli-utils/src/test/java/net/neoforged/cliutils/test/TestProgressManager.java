package net.neoforged.cliutils.test;

import net.neoforged.cliutils.progress.ProgressInterceptor;
import net.neoforged.cliutils.progress.ProgressManager;
import net.neoforged.cliutils.progress.ProgressReporter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringWriter;

public class TestProgressManager {

    @Test
    void testStep() {
        final TestSuite suite = new TestSuite();
        suite.in.setStep("Doing stuff");
        assert suite.out.step.equals("Doing stuff");
        assert suite.allowedOut.toString().isEmpty();
    }

    @Test
    void testMaxProgress() {
        final TestSuite suite = new TestSuite();
        suite.in.setMaxProgress(10);
        assert suite.out.maxProgress == 10;
    }

    @Test
    void testPercentageProgress() {
        final TestSuite suite = new TestSuite();
        suite.in.setPercentageProgress(0.11);
        assert suite.out.maxProgress == 100;
        assert suite.out.progress == 11;
    }

    @Test
    void testProgress() {
        final TestSuite suite = new TestSuite();
        suite.in.setProgress(145);
        assert suite.out.progress == 145;
    }

    @Test
    void testIndeterminate() {
        final TestSuite suite = new TestSuite();
        suite.in.setIndeterminate(true);
        assert suite.out.indeterminate;
    }

    private static class TestSuite {
        public final ProgressManagerImpl out = new ProgressManagerImpl();
        public final StringWriter allowedOut = new StringWriter();
        private final PrintStream stream = new PrintStream(new ProgressInterceptor(new OutputStream() {
            @Override
            public void write(int b) {
                allowedOut.write(b);
            }
        }, out));
        public final ProgressManager in = new ProgressReporter(true, stream);
    }

    private static class ProgressManagerImpl implements ProgressManager {
        public int maxProgress = 0, progress = 0;
        public boolean indeterminate = false;
        public String step;
        @Override
        public void setMaxProgress(int maxProgress) {
            this.maxProgress = maxProgress;
        }

        @Override
        public void setProgress(int progress) {
            this.progress = progress;
        }

        @Override
        public void setPercentageProgress(double progress) {
            if (maxProgress != 100) setMaxProgress(100);
            setProgress((int) (progress * 100));
        }

        @Override
        public void setStep(String name) {
            this.step = name;
        }

        @Override
        public void setIndeterminate(boolean indeterminate) {
            this.indeterminate = indeterminate;
        }
    }
}
