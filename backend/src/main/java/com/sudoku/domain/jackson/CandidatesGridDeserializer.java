package com.sudoku.domain.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.sudoku.domain.CandidatesGrid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deserializes {@code {"rows": [[[...], ...], ...]}} into a {@link CandidatesGrid}.
 */
public class CandidatesGridDeserializer extends StdDeserializer<CandidatesGrid> {

    public CandidatesGridDeserializer() {
        super(CandidatesGrid.class);
    }

    @Override
    public CandidatesGrid deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        JsonNode root = p.getCodec().readTree(p);
        JsonNode rowsNode = root.get("rows");
        List<List<List<Integer>>> rows = new ArrayList<>(9);
        for (JsonNode rowNode : rowsNode) {
            List<List<Integer>> row = new ArrayList<>(9);
            for (JsonNode cellNode : rowNode) {
                List<Integer> candidates = new ArrayList<>();
                for (JsonNode val : cellNode) {
                    candidates.add(val.intValue());
                }
                row.add(Collections.unmodifiableList(candidates));
            }
            rows.add(Collections.unmodifiableList(row));
        }
        return CandidatesGrid.of(Collections.unmodifiableList(rows));
    }
}
