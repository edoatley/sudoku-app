package com.sudoku.puzzle.developer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads hint demo grids from JSON config files in {@code resources/developer/}.
 *
 * <p>Each file is named {@code hint-demo-<slug>.json} and contains:
 * <pre>
 * {
 *   "slug": "naked-pair",
 *   "description": "...",
 *   "grid": [[...], ...]
 * }
 * </pre>
 *
 * <p>To add a new demo grid for a new strategy: drop a new JSON file in
 * {@code src/main/resources/developer/} following the naming convention.
 * No Java code changes are needed.
 */
public final class HintDemoGrids {

    private static final String[] KNOWN_SLUGS = {
            "full-house",
            "naked-single",
            "naked-pair",
            "hidden-single",
            "pointing-pair",
            "naked-triple",
            "hidden-pair",
            "hidden-triple",
            "x-wing",
            "swordfish",
            "y-wing",
    };

    private static final Map<String, List<List<Integer>>> GRIDS;

    static {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<List<Integer>>> grids = new LinkedHashMap<>();
        for (String slug : KNOWN_SLUGS) {
            String path = "/developer/hint-demo-" + slug + ".json";
            try (InputStream is = HintDemoGrids.class.getResourceAsStream(path)) {
                if (is == null) {
                    throw new IllegalStateException("Missing demo grid resource: " + path);
                }
                JsonNode root = mapper.readTree(is);
                JsonNode gridNode = root.get("grid");
                List<List<Integer>> grid = new ArrayList<>(9);
                for (JsonNode rowNode : gridNode) {
                    List<Integer> row = new ArrayList<>(9);
                    for (JsonNode cell : rowNode) {
                        row.add(cell.intValue());
                    }
                    grid.add(Collections.unmodifiableList(row));
                }
                grids.put(slug, Collections.unmodifiableList(grid));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load demo grid for slug '" + slug + "'", e);
            }
        }
        GRIDS = Collections.unmodifiableMap(grids);
    }

    private HintDemoGrids() {}

    /**
     * Returns the demo grid for the given technique slug, or {@code null} if unknown.
     */
    public static List<List<Integer>> forSlug(String slug) {
        return GRIDS.get(slug);
    }

    /**
     * Returns all registered slugs in registration order.
     */
    public static Set<String> slugs() {
        return GRIDS.keySet();
    }
}
