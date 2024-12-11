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
package net.neoforged.installertools.cli.progress;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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

    private StringBuffer current;

    @Override
    public void write(int b) throws IOException {
        if (current != null) {
            if (b == '\n') {
                processCurrent();
            } else if (b != '\r') // Ignore CR because Windows is bad
                current.append((char)b);
        } else {
            if (b == '\033') {
                current = new StringBuffer()
                        .append((char)b);
            } else {
                out.write(b);
            }
        }
    }

    private void processCurrent() throws IOException {
        final String cur = current.toString();
        if (cur.startsWith(FULL_MODIFIER)) {
            final String[] values = cur.substring(FULL_MODIFIER.length()).split(" ", 2);
            // If not 2, the value's empty, which isn't supported, so ignore
            if (values.length == 2) {
                final String first = values[0];
                if (first.length() != 1) {
                    System.err.println("Invalid progress modifier: " + values[0]);
                    return;
                }

                final ProgressActionType action = ProgressActionType.TYPES.get(first.charAt(0));
                if (action == null) {
                    System.err.println("Unknown progress modifier: " + first.charAt(0));
                    return;
                }

                action.acceptor.accept(manager, values[1]);
            }
        } else {
            out.write(cur.getBytes(StandardCharsets.UTF_8));
            out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
        }

        current = null;
    }
}
