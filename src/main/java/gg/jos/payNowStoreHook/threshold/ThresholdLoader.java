package gg.jos.payNowStoreHook.threshold;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ThresholdLoader {

    private final FileConfiguration configuration;

    public ThresholdLoader(FileConfiguration configuration) {
        this.configuration = configuration;
    }

    public List<Threshold> load() {
        List<Map<?, ?>> definitions = configuration.getMapList("thresholds");
        List<RawThreshold> rawThresholds = new ArrayList<>();
        for (Map<?, ?> definition : definitions) {
            double amount = parseAmount(definition.get("amount"));
            List<String> commands = parseList(definition.get("commands"));
            List<String> undoCommands = parseList(definition.get("undo_commands"));
            rawThresholds.add(new RawThreshold(amount, commands, undoCommands));
        }

        rawThresholds.sort((a, b) -> Double.compare(a.amount(), b.amount()));
        List<Threshold> thresholds = new ArrayList<>();
        for (int i = 0; i < rawThresholds.size(); i++) {
            RawThreshold raw = rawThresholds.get(i);
            thresholds.add(new Threshold(raw.amount(), raw.commands(), raw.undoCommands(), i));
        }
        return List.copyOf(thresholds);
    }

    private double parseAmount(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String raw) {
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0;
    }

    private List<String> parseList(Object value) {
        if (value instanceof List<?> list) {
            List<String> commands = new ArrayList<>();
            for (Object entry : list) {
                if (entry != null && !entry.toString().isBlank()) {
                    commands.add(entry.toString());
                }
            }
            return List.copyOf(commands);
        }
        return List.of();
    }

    private record RawThreshold(double amount, List<String> commands, List<String> undoCommands) {
    }
}
