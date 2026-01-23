package gg.jos.paynowstorehook.threshold;

import java.util.List;

public record Threshold(double amount, List<String> commands, List<String> undoCommands, int index) {
}
