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

import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ProgressActionType {
    STEP('s', ProgressManager::setStep),
    PROGRESS('p', (progressManager, value) -> {
        if (value.endsWith("%")) {
            progressManager.setPercentageProgress(Double.parseDouble(value.substring(0, value.length() - 1)));
        } else {
            progressManager.setProgress(Integer.parseInt(value));
        }
    }),
    MAX_PROGRESS('m', (progressManager, value) -> progressManager.setMaxProgress(Integer.parseInt(value))),
    INDETERMINATE('i', (progressManager, value) -> progressManager.setIndeterminate(Boolean.parseBoolean(value)));

    public static final Map<Character, ProgressActionType> TYPES = Arrays.stream(values())
            .collect(Collectors.toMap(val -> val.identifier, Function.identity()));

    public final char identifier;
    public final BiConsumer<ProgressManager, String> acceptor;

    ProgressActionType(char identifier, BiConsumer<ProgressManager, String> acceptor) {
        this.identifier = identifier;
        this.acceptor = acceptor;
    }
}
