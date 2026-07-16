package com.sudoku.coach.bedrock;

import java.util.List;
import java.util.regex.Pattern;

// @spec SC-BE-030
/**
 * Converts 0-based coordinates and unit indices in backend-generated hint text (nudge/focus/
 * reveal) to human-readable 1-based (cells/rows/columns) or named (blocks) equivalents, before
 * that text is injected into the Bedrock prompt context.
 *
 * <p>The hint engine is 0-indexed internally by design — conversion is applied only at display
 * layers, never to the underlying {@code HintResponse} (see {@code HE-UI-005}). The coach's
 * prompt context is effectively a second display layer alongside the deterministic hint dialog,
 * so this mirrors {@code ui/src/utils/hintDisplay.js}'s {@code formatHintText()} rather than
 * reusing it (cross-language boundary). All transformations are pure text formatting.
 */
final class HintTextFormatter {

    private HintTextFormatter() {}

    private static final List<String> BLOCK_NAMES = List.of(
            "top-left", "top-middle", "top-right",
            "middle-left", "centre", "middle-right",
            "bottom-left", "bottom-middle", "bottom-right");

    private static final Pattern CELL_COORDINATE = Pattern.compile("\\(([0-8]),\\s*([0-8])\\)");
    private static final Pattern BLOCK_REFERENCE = Pattern.compile("\\b[Bb]lock ([0-8])\\b");
    private static final Pattern TRIPLE_UNIT = Pattern.compile("\\b(rows|columns) ([0-8]),\\s*([0-8]) and ([0-8])\\b");
    private static final Pattern PAIR_UNIT = Pattern.compile("\\b(rows|columns) ([0-8]) and ([0-8])\\b");
    private static final Pattern SINGLE_UNIT = Pattern.compile("\\b([Rr]ow|[Cc]olumn) ([0-8])\\b");

    static String format(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;
        result = CELL_COORDINATE.matcher(result).replaceAll(m ->
                "(" + (Integer.parseInt(m.group(1)) + 1) + ", " + (Integer.parseInt(m.group(2)) + 1) + ")");
        result = BLOCK_REFERENCE.matcher(result).replaceAll(m -> BLOCK_NAMES.get(Integer.parseInt(m.group(1))));
        result = TRIPLE_UNIT.matcher(result).replaceAll(m ->
                m.group(1) + " " + (Integer.parseInt(m.group(2)) + 1) + ", " + (Integer.parseInt(m.group(3)) + 1)
                        + " and " + (Integer.parseInt(m.group(4)) + 1));
        result = PAIR_UNIT.matcher(result).replaceAll(m ->
                m.group(1) + " " + (Integer.parseInt(m.group(2)) + 1) + " and " + (Integer.parseInt(m.group(3)) + 1));
        result = SINGLE_UNIT.matcher(result).replaceAll(m ->
                m.group(1) + " " + (Integer.parseInt(m.group(2)) + 1));
        return result;
    }
}
