package com.sudoku.domain.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.sudoku.domain.Grid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deserializes {@code {"rows": [[...], ...]}} into a {@link Grid}.
 */
public class GridDeserializer extends StdDeserializer<Grid> {

    public GridDeserializer() {
        super(Grid.class);
    }

    @Override
    public Grid deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        JsonNode root = p.getCodec().readTree(p);
        JsonNode rowsNode = root.get("rows");
        List<List<Integer>> rows = new ArrayList<>(9);
        for (JsonNode rowNode : rowsNode) {
            List<Integer> row = new ArrayList<>(9);
            for (JsonNode cell : rowNode) {
                row.add(cell.intValue());
            }
            rows.add(Collections.unmodifiableList(row));
        }
        return Grid.of(Collections.unmodifiableList(rows));
    }
}
