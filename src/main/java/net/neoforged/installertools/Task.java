/*
 * InstallerTools
 * Copyright (c) 2019-2025.
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
package net.neoforged.installertools;

import net.neoforged.cliutils.progress.ProgressReporter;

import java.io.IOException;

public abstract class Task {
    protected static final ProgressReporter PROGRESS = ProgressReporter.getDefault();

    public abstract void process(String[] args) throws IOException;

    protected void error(String message) {
        log(message);
        throw new RuntimeException(message);
    }

    protected void log(String message) {
        System.out.println(message);
    }
}
