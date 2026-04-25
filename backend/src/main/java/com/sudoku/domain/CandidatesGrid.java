package com.sudoku.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.sudoku.domain.jackson.CandidatesGridDeserializer;
import com.sudoku.domain.jackson.CandidatesGridSerializer;

import java.util.List;

// @spec DT-CGRID-001, DT-CGRID-002, DT-CGRID-003, DT-CGRID-004, DT-CGRID-005

/**
 * A 9×9 grid of candidate digit sets. Each inner list is the sorted valid candidates for that cell;
 * an empty list means the cell already has a placed value.
 *
 * <p>Serialized over the wire as {@code {"rows": [[[...], ...], ...]}} so the field name is
 * explicit in the JSON contract.
 */
@JsonSerialize(using = CandidatesGridSerializer.class)
@JsonDeserialize(using = CandidatesGridDeserializer.class)
public record CandidatesGrid(List<List<List<Integer>>> rows) {

    /** Static factory — preferred over the canonical constructor at internal call sites. */
    public static CandidatesGrid of(List<List<List<Integer>>> rows) {
        return new CandidatesGrid(rows);
    }

    /** Returns the list of candidate lists for row {@code i}. */
    public List<List<Integer>> row(int i) {
        return rows.get(i);
    }

    /** Returns the candidate list for the cell at row {@code r}, column {@code c}. */
    public List<Integer> candidates(int r, int c) {
        return rows.get(r).get(c);
    }
}
