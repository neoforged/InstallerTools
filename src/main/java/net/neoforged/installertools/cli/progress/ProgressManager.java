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

/**
 * A manager that changes the progress of a progress bar, to indicate the current progress to users.
 */
public interface ProgressManager {
    /**
     * Sets the max progress of the manager.
     */
    void setMaxProgress(int maxProgress);

    /**
     * Sets the current progress of the manager.
     */
    void setProgress(int progress);

    /**
     * Sets progress of the manager, based on a fractional value. <br>
     * Note: this also sets the {@link #setMaxProgress(int)} to {@literal 100}.
     */
    void setPercentageProgress(double progress);

    /**
     * Sets the current step to be shown to the user.
     */
    void setStep(String name);

    /**
     * Sets whether the max progress is known or not. If not, the loading bar will load 'forever'.
     */
    void setIndeterminate(boolean indeterminate);
}
