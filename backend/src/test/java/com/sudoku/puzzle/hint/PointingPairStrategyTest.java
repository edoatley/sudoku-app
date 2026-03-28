package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.dto.CandidateElimination;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PointingPairStrategyTest {

    private PointingPairStrategy strategy;

    // A mostly-empty grid so we can fully control candidate sets via patching
    private static final List<List<Integer>> SPARSE_GRID = List.of(
            List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(0, 0, 0, 0, 0, 0, 0, 0, 0)
    );

    private static final List<List<Integer>> SOLVED_GRID = List.of(
            List.of(5, 3, 4, 6, 7, 8, 9, 1, 2),
            List.of(6, 7, 2, 1, 9, 5, 3, 4, 8),
            List.of(1, 9, 8, 3, 4, 2, 5, 6, 7),
            List.of(8, 5, 9, 7, 6, 1, 4, 2, 3),
            List.of(4, 2, 6, 8, 5, 3, 7, 9, 1),
            List.of(7, 1, 3, 9, 2, 4, 8, 5, 6),
            List.of(9, 6, 1, 5, 3, 7, 2, 8, 4),
            List.of(2, 8, 7, 4, 1, 9, 6, 3, 5),
            List.of(3, 4, 5, 2, 8, 6, 1, 7, 9)
    );

    @BeforeEach
    void setUp() {
        strategy = new PointingPairStrategy();
    }

    @Test
    void pointingPair_rowLocked_returnsHint() {
        // Digit 7 is a candidate only in cells (1,0) and (1,1) within block 0 (rows 0-2, cols 0-2).
        // Digit 7 also appears as a candidate in (1,5) and (1,7) — outside block 0 on row 1.
        // All other cells in block 0 have no candidates, and row 1 cols 3,4,6,8 also have no candidates,
        // so the only eliminations are (1,5) and (1,7).
        Board board = Board.fromGrid(SPARSE_GRID);

        // Clear all auto-computed candidates first by setting every cell to empty set
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                board.getCell(r, c).setCandidates(Set.of());
            }
        }

        // Place digit 7 in block 0 row 1, cols 0 and 1 (both same row → row-locked)
        board.getCell(1, 0).setCandidates(Set.of(7));
        board.getCell(1, 1).setCandidates(Set.of(7));

        // Place digit 7 in row 1 outside block 0 (cols 3-8), so eliminations exist
        board.getCell(1, 5).setCandidates(Set.of(7));
        board.getCell(1, 7).setCandidates(Set.of(7));

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isPresent());
        HintResponse hint = result.get();

        // Highlight cells must be the two pointing cells within block 0
        List<Coordinate> highlights = hint.highlightCells();
        assertEquals(2, highlights.size());
        assertTrue(highlights.contains(new Coordinate(1, 0)));
        assertTrue(highlights.contains(new Coordinate(1, 1)));

        // Eliminations must be the two cells outside block 0 in row 1
        List<CandidateElimination> elims = hint.eliminatedCandidates();
        assertEquals(2, elims.size());
        assertTrue(elims.contains(new CandidateElimination(1, 5, 7)));
        assertTrue(elims.contains(new CandidateElimination(1, 7, 7)));

        // Solving strategy returns no solved cells
        assertTrue(hint.solvedCells().isEmpty());
    }

    @Test
    void pointingPair_colLocked_returnsHint() {
        // Digit 3 is a candidate only in cells (0,3) and (1,3) within block 1 (rows 0-2, cols 3-5).
        // Digit 3 also appears in (4,3) and (7,3) — outside block 1 in column 3.
        Board board = Board.fromGrid(SPARSE_GRID);

        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                board.getCell(r, c).setCandidates(Set.of());
            }
        }

        // Place digit 3 in block 1 column 3, rows 0 and 1 (both same col → col-locked)
        board.getCell(0, 3).setCandidates(Set.of(3));
        board.getCell(1, 3).setCandidates(Set.of(3));

        // Place digit 3 in column 3 outside block 1 (rows 3-8)
        board.getCell(4, 3).setCandidates(Set.of(3));
        board.getCell(7, 3).setCandidates(Set.of(3));

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isPresent());
        HintResponse hint = result.get();

        List<Coordinate> highlights = hint.highlightCells();
        assertEquals(2, highlights.size());
        assertTrue(highlights.contains(new Coordinate(0, 3)));
        assertTrue(highlights.contains(new Coordinate(1, 3)));

        List<CandidateElimination> elims = hint.eliminatedCandidates();
        assertEquals(2, elims.size());
        assertTrue(elims.contains(new CandidateElimination(4, 3, 3)));
        assertTrue(elims.contains(new CandidateElimination(7, 3, 3)));

        assertTrue(hint.solvedCells().isEmpty());
    }

    @Test
    void pointingPair_solvedBoard_returnsEmpty() {
        Board board = Board.fromGrid(SOLVED_GRID);
        board.calculateAllCandidates();

        assertTrue(strategy.evaluate(board).isEmpty());
    }

    @Test
    void pointingPair_metadata() {
        Board board = Board.fromGrid(SPARSE_GRID);
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                board.getCell(r, c).setCandidates(Set.of());
            }
        }
        board.getCell(1, 0).setCandidates(Set.of(7));
        board.getCell(1, 1).setCandidates(Set.of(7));
        board.getCell(1, 5).setCandidates(Set.of(7));

        assertEquals(50, strategy.getDifficultyRank());
        HintResponse hint = strategy.evaluate(board).orElseThrow();
        assertEquals("pointing-pair", hint.markdownSlug());
        assertEquals("medium", hint.difficulty());
    }
}
