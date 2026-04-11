package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.dto.CoordinateCandidate;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NakedTripleStrategyTest {

    private NakedTripleStrategy strategy;

    private static final List<List<Integer>> NAKED_TRIPLE_GRID = List.of(
            List.of(0, 0, 0, 2, 6, 9, 7, 8, 1),
            List.of(0, 0, 0, 5, 7, 1, 4, 6, 3),
            List.of(0, 0, 0, 4, 8, 3, 9, 5, 2),
            List.of(8, 1, 9, 0, 0, 0, 0, 0, 0),
            List.of(2, 7, 6, 0, 0, 0, 0, 0, 0),
            List.of(3, 4, 5, 0, 0, 0, 0, 0, 0),
            List.of(9, 3, 4, 0, 0, 0, 0, 0, 0),
            List.of(6, 5, 8, 0, 0, 0, 0, 0, 0),
            List.of(1, 2, 7, 0, 0, 0, 0, 0, 0)
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
        strategy = new NakedTripleStrategy();
    }

    @Test
    void nakedTriple_syntheticRowTriple_returnsHint() {
        // Row 3 has 6 empty cells (cols 3-8); form a naked triple at cols 3,4,5 with union {1,2,3}
        // and ensure cols 6,7,8 contain at least one of those digits so eliminations exist.
        Board board = Board.fromGrid(NAKED_TRIPLE_GRID);
        board.calculateAllCandidates();

        board.getCell(3, 3).setCandidates(Set.of(1, 2));
        board.getCell(3, 4).setCandidates(Set.of(2, 3));
        board.getCell(3, 5).setCandidates(Set.of(1, 3));
        // Patch a non-triple cell to contain one of the triple digits so an elimination exists
        board.getCell(3, 6).setCandidates(Set.of(1, 4));

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isPresent(), "Should detect naked triple");
        HintResponse hint = result.get();

        List<Coordinate> highlights = hint.highlightCells();
        assertEquals(3, highlights.size());
        assertTrue(highlights.contains(new Coordinate(3, 3)));
        assertTrue(highlights.contains(new Coordinate(3, 4)));
        assertTrue(highlights.contains(new Coordinate(3, 5)));

        List<CoordinateCandidate> elims = hint.eliminatedCandidates();
        assertFalse(elims.isEmpty());
        for (CoordinateCandidate e : elims) {
            assertTrue(e.value() == 1 || e.value() == 2 || e.value() == 3,
                    "Eliminations must be for triple digits only");
        }

        assertTrue(hint.solvedCells().isEmpty());
    }

    @Test
    void nakedTriple_solvedBoard_returnsEmpty() {
        Board board = Board.fromGrid(SOLVED_GRID);
        board.calculateAllCandidates();

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isEmpty());
    }

    @Test
    void nakedTriple_difficultyRank_is60() {
        assertEquals(60, strategy.getDifficultyRank());
    }

    @Test
    void nakedTriple_markdownSlug_and_difficulty() {
        Board board = Board.fromGrid(NAKED_TRIPLE_GRID);
        board.calculateAllCandidates();
        board.getCell(3, 3).setCandidates(Set.of(1, 2));
        board.getCell(3, 4).setCandidates(Set.of(2, 3));
        board.getCell(3, 5).setCandidates(Set.of(1, 3));
        board.getCell(3, 6).setCandidates(Set.of(1, 4));

        HintResponse hint = strategy.evaluate(board).orElseThrow();
        assertEquals("naked-triple", hint.markdownSlug());
        assertEquals("medium", hint.difficulty());
        assertEquals("Naked Triple", hint.techniqueName());
    }
}
