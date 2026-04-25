package com.sudoku.domain.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.sudoku.domain.CandidatesGrid;

import java.io.IOException;
import java.util.List;

/**
 * Serializes {@link CandidatesGrid} as {@code {"rows": [[[...], ...], ...]}} so the wire
 * format carries an explicit field name rather than a bare array.
 */
public class CandidatesGridSerializer extends StdSerializer<CandidatesGrid> {

    public CandidatesGridSerializer() {
        super(CandidatesGrid.class);
    }

    @Override
    public void serialize(CandidatesGrid grid, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeFieldName("rows");
        gen.writeStartArray();
        for (List<List<Integer>> row : grid.rows()) {
            gen.writeStartArray();
            for (List<Integer> candidates : row) {
                gen.writeStartArray();
                for (Integer val : candidates) {
                    gen.writeNumber(val);
                }
                gen.writeEndArray();
            }
            gen.writeEndArray();
        }
        gen.writeEndArray();
        gen.writeEndObject();
    }
}
