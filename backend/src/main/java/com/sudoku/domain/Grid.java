package com.sudoku.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.sudoku.domain.jackson.GridDeserializer;
import com.sudoku.domain.jackson.GridSerializer;

import java.util.List;

// @spec DT-GRID-001, DT-GRID-002, DT-GRID-003, DT-GRID-004, DT-GRID-005, DT-GRID-006

/**
 * A 9×9 Sudoku grid. Each cell is 0 (empty) or 1–9.
 *
 * <p>Serialized over the wire as {@code {"rows": [[...], ...]}} so the field name is
 * explicit in the JSON contract. The custom serializer/deserializer handle this transparently.
 */
@JsonSerialize(using = GridSerializer.class)
@JsonDeserialize(using = GridDeserializer.class)
public record Grid(List<List<Integer>> rows) {

    /** Static factory — preferred over the canonical constructor at internal call sites. */
    public static Grid of(List<List<Integer>> rows) {
        return new Grid(rows);
    }

    /** Returns the list of cell values for row {@code i}. */
    public List<Integer> row(int i) {
        return rows.get(i);
    }

    /** Returns the cell value at row {@code r}, column {@code c}. */
    public int cell(int r, int c) {
        return rows.get(r).get(c);
    }

    /** Returns the number of rows (always 9 for a valid grid). */
    public int size() {
        return rows.size();
    }
}
