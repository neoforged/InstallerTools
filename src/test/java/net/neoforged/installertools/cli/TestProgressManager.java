/*
 * InstallerTools
 * Copyright (c) 2019-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.neoforged.installertools.cli;

import net.neoforged.installertools.cli.progress.ProgressInterceptor;
import net.neoforged.installertools.cli.progress.ProgressManager;
import net.neoforged.installertools.cli.progress.ProgressReporter;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
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

    @Test
    void testWriteIsForwarded() {
        final TestSuite suite = new TestSuite();

        suite.in.setProgress(10);
        assert suite.allowedOut.toString().isEmpty();

        suite.stream.println("abcd");
        assert suite.allowedOut.toString().equals("abcd" + System.lineSeparator());

        suite.stream.println("\033jkas");
        assert suite.allowedOut.toString().equals("abcd" + System.lineSeparator() + "\033jkas" + System.lineSeparator());
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
