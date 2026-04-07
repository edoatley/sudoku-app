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

class HiddenTripleStrategyTest {

    private HiddenTripleStrategy strategy;

    private static final List<List<Integer>> EASY_GRID = List.of(
            List.of(5, 3, 0, 0, 7, 0, 0, 0, 0),
            List.of(6, 0, 0, 1, 9, 5, 0, 0, 0),
            List.of(0, 9, 8, 0, 0, 0, 0, 6, 0),
            List.of(8, 0, 0, 0, 6, 0, 0, 0, 3),
            List.of(4, 0, 0, 8, 0, 3, 0, 0, 1),
            List.of(7, 0, 0, 0, 2, 0, 0, 0, 6),
            List.of(0, 6, 0, 0, 0, 0, 2, 8, 0),
            List.of(0, 0, 0, 4, 1, 9, 0, 0, 5),
            List.of(0, 0, 0, 0, 8, 0, 0, 7, 9)
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
        strategy = new HiddenTripleStrategy();
    }

    @Test
    void hiddenTriple_syntheticRowTriple_returnsHint() {
        Board board = Board.fromGrid(EASY_GRID);
        board.calculateAllCandidates();

        // Force a hidden triple of digits 1, 2, 3 in row 0:
        // Only cells (0,2), (0,5), (0,7) contain 1, 2, 3; each has extra candidates making it hidden.
        board.getCell(0, 2).setCandidates(Set.of(1, 2, 4));   // has 1,2 + extra 4
        board.getCell(0, 3).setCandidates(Set.of(4, 8));       // no 1,2,3
        board.getCell(0, 5).setCandidates(Set.of(2, 3, 8));   // has 2,3 + extra 8
        board.getCell(0, 6).setCandidates(Set.of(4, 8));       // no 1,2,3
        board.getCell(0, 7).setCandidates(Set.of(1, 3, 4));   // has 1,3 + extra 4
        board.getCell(0, 8).setCandidates(Set.of(4, 8));       // no 1,2,3

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isPresent());
        HintResponse hint = result.get();

        List<Coordinate> highlights = hint.highlightCells();
        assertEquals(3, highlights.size());
        assertTrue(highlights.contains(new Coordinate(0, 2)));
        assertTrue(highlights.contains(new Coordinate(0, 5)));
        assertTrue(highlights.contains(new Coordinate(0, 7)));

        List<CandidateElimination> elims = hint.eliminatedCandidates();
        assertFalse(elims.isEmpty());
        for (CandidateElimination e : elims) {
            assertFalse(e.value() == 1 || e.value() == 2 || e.value() == 3,
                    "Eliminated candidates must not be the hidden-triple digits");
        }

        assertTrue(hint.solvedCells().isEmpty());
    }

    @Test
    void hiddenTriple_solvedBoard_returnsEmpty() {
        Board board = Board.fromGrid(SOLVED_GRID);
        board.calculateAllCandidates();

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isEmpty());
    }

    @Test
    void hiddenTriple_markdownSlug_and_difficulty() {
        Board board = Board.fromGrid(EASY_GRID);
        board.calculateAllCandidates();
        board.getCell(0, 2).setCandidates(Set.of(1, 2, 4));
        board.getCell(0, 3).setCandidates(Set.of(4, 8));
        board.getCell(0, 5).setCandidates(Set.of(2, 3, 8));
        board.getCell(0, 6).setCandidates(Set.of(4, 8));
        board.getCell(0, 7).setCandidates(Set.of(1, 3, 4));
        board.getCell(0, 8).setCandidates(Set.of(4, 8));

        HintResponse hint = strategy.evaluate(board).orElseThrow();
        assertEquals("hidden-triple", hint.markdownSlug());
        assertEquals("hard", hint.difficulty());
        assertEquals(80, strategy.getDifficultyRank());
    }
}
