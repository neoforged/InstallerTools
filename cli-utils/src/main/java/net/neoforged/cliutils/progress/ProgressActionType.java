package net.neoforged.cliutils.progress;

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
