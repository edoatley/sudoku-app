package com.sudoku.domain.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.sudoku.domain.Grid;

import java.io.IOException;
import java.util.List;

/**
 * Serializes {@link Grid} as {@code {"rows": [[...], ...]}} so the wire format
 * carries an explicit field name rather than a bare array.
 */
public class GridSerializer extends StdSerializer<Grid> {

    public GridSerializer() {
        super(Grid.class);
    }

    @Override
    public void serialize(Grid grid, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeFieldName("rows");
        gen.writeStartArray();
        for (List<Integer> row : grid.rows()) {
            gen.writeStartArray();
            for (Integer val : row) {
                gen.writeNumber(val);
            }
            gen.writeEndArray();
        }
        gen.writeEndArray();
        gen.writeEndObject();
    }
}
