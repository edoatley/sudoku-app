package com.sudoku.puzzle.developer;

import com.sudoku.domain.Board;
import com.sudoku.dto.ActionableCell;
import com.sudoku.dto.CandidateElimination;
import com.sudoku.dto.HintResponse;
import com.sudoku.puzzle.hint.*;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * Scratch test to find which puzzle strings from sudokuwiki.org work
 * with our strategy implementations. DELETE after puzzle selection is done.
 */
class PuzzleCandidateFinderTest {

    // --- X-Wing candidates from sudokuwiki.org and hodoku ---
    private static final String[] X_WING_CANDIDATES = {
        "100000569402000008050009040000640801000010000208035000040500010900000402621000005",
        "000000004760010050090002081070050010000709000080030060240100070010090045900000000",
        "000001008700030009020000061080009003001040900900300020240000080600090005100600000",
        "000001408000206030720000009030700000400605003000002040900000062060903000502400000",
        "000080000000901084209040006790000800040000050006000042900060501160704000000030000",
        // hodoku bf201
        "000000000760003002002640009040390007000000493005000020001560000370090041000000060",
        // hodoku bf202
        "900062700005003000000067000030000000900008020045009003500350128024000005010000000",
    };

    // --- Swordfish candidates from sudokuwiki.org and hodoku ---
    private static final String[] SWORDFISH_CANDIDATES = {
        "500010003006003002003200000002300076000050000190007500000009400200800600900040005",
        "900000000037010420840000603000034810000060000068120000102000084085070360000000001",
        "020040069003806200060020000890500010000000000030001026000010070009604300270050090",
        // hodoku bf301
        "160540007008001300308000007700509060009020570000000300003004000000001600164500000",
        // hodoku bf302 (cleaned up - the raw string from hodoku was corrupted, using dot-format converted)
        "100000345003020078008000000060500300050000400030000060298020610000000000780900000",
    };

    // --- Y-Wing candidates from sudokuwiki.org and hodoku XY-Wing ---
    private static final String[] Y_WING_CANDIDATES = {
        "050000080000086000000201070009020601280000054703060900090605000000170000030000010",
        "009600000000025090400001078901040800000000000002070906370800002020510000000002400",
        "009765000600400007070000500090024000500000003000910070005000090100007004000156800",
        "100007600008010020306020000020000075000385000450000080000040203010030800009700004",
        "000000001004060208070320400900018000005000600000540009008037040609080300100000000",
        // hodoku xy01 (XY-Wing = Y-Wing)
        "000060000001086300030090009904000003000007045748200000000658069009007000000040003",
    };

    private static final HintStrategy[] SIMPLER_THAN_X_WING = {
        new FullHouseStrategy(), new NakedSingleStrategy(),
        new NakedPairStrategy(), new HiddenSingleStrategy(),
        new PointingPairStrategy(), new NakedTripleStrategy(),
        new HiddenPairStrategy(), new HiddenTripleStrategy()
    };

    private static final HintStrategy[] SIMPLER_THAN_SWORDFISH = {
        new FullHouseStrategy(), new NakedSingleStrategy(),
        new NakedPairStrategy(), new HiddenSingleStrategy(),
        new PointingPairStrategy(), new NakedTripleStrategy(),
        new HiddenPairStrategy(), new HiddenTripleStrategy(),
        new XWingStrategy()
    };

    private static final HintStrategy[] SIMPLER_THAN_Y_WING = {
        new FullHouseStrategy(), new NakedSingleStrategy(),
        new NakedPairStrategy(), new HiddenSingleStrategy(),
        new PointingPairStrategy(), new NakedTripleStrategy(),
        new HiddenPairStrategy(), new HiddenTripleStrategy(),
        new XWingStrategy(), new SwordfishStrategy()
    };

    // current JSON demo grids
    private static final String CURRENT_X_WING_DEMO  = "100000569492056108056109240009640801064010000218035604040900016901060400620001005";
    private static final String CURRENT_SWORDFISH_DEMO = "000600080020009005087000090000000300413050279009000000090000130500900060060005000";
    private static final String CURRENT_Y_WING_DEMO  = "900003060000000005070260000500031070603000904080970003000057090200000000090800007";

    @Test
    void checkCurrentDemoGridsOnRawGrid() {
        System.out.println("\n=== CURRENT DEMO GRIDS ON RAW GRID ===");
        for (String[] entry : new String[][]{
                {"x-wing", CURRENT_X_WING_DEMO},
                {"swordfish", CURRENT_SWORDFISH_DEMO},
                {"y-wing", CURRENT_Y_WING_DEMO}}) {
            String name = entry[0], puzzle = entry[1];
            Board board = rawBoard(fromString(puzzle));
            boolean xw = new XWingStrategy().evaluate(board).isPresent();
            boolean sf = new SwordfishStrategy().evaluate(board).isPresent();
            boolean yw = new YWingStrategy().evaluate(board).isPresent();
            System.out.println(name + ": X-Wing=" + xw + " Swordfish=" + sf + " Y-Wing=" + yw);
        }
    }

    @Test
    void findValidXWingPuzzle() {
        System.out.println("\n=== X-WING CANDIDATES ===");
        for (String puzzle : X_WING_CANDIDATES) {
            Board board = autocompleteToBoard(fromString(puzzle), SIMPLER_THAN_X_WING);
            long empty = countEmpty(board);
            boolean xWingFires = new XWingStrategy().evaluate(board).isPresent();
            boolean hasElims = new XWingStrategy().evaluate(board)
                .map(h -> !h.eliminatedCandidates().isEmpty()).orElse(false);
            System.out.println("Puzzle: " + puzzle.substring(0, 20) + "...");
            System.out.println("  Empty after simpler: " + empty + ", X-Wing fires: " + xWingFires + ", has elims: " + hasElims);
            if (xWingFires && hasElims && empty > 0) {
                System.out.println("  *** VALID X-WING PUZZLE ***");
                printGrid(puzzle);
            }
        }
    }

    @Test
    void findValidSwordfishPuzzle() {
        System.out.println("\n=== SWORDFISH CANDIDATES ===");
        for (String puzzle : SWORDFISH_CANDIDATES) {
            Board board = autocompleteToBoard(fromString(puzzle), SIMPLER_THAN_SWORDFISH);
            long empty = countEmpty(board);
            boolean fires = new SwordfishStrategy().evaluate(board).isPresent();
            boolean hasElims = new SwordfishStrategy().evaluate(board)
                .map(h -> !h.eliminatedCandidates().isEmpty()).orElse(false);
            System.out.println("Puzzle: " + puzzle.substring(0, 20) + "...");
            System.out.println("  Empty after simpler: " + empty + ", Swordfish fires: " + fires + ", has elims: " + hasElims);
            if (empty > 0 && !fires) {
                // check if X-Wing resolves it
                boolean xWingFires = new XWingStrategy().evaluate(board).isPresent();
                System.out.println("  (stuck: X-Wing fires=" + xWingFires + ")");
            }
            if (fires && hasElims && empty > 0) {
                System.out.println("  *** VALID SWORDFISH PUZZLE ***");
                printGrid(puzzle);
            }
        }
    }

    @Test
    void findValidYWingPuzzle() {
        System.out.println("\n=== Y-WING CANDIDATES ===");
        for (String puzzle : Y_WING_CANDIDATES) {
            Board board = autocompleteToBoard(fromString(puzzle), SIMPLER_THAN_Y_WING);
            long empty = countEmpty(board);
            boolean fires = new YWingStrategy().evaluate(board).isPresent();
            boolean hasElims = new YWingStrategy().evaluate(board)
                .map(h -> !h.eliminatedCandidates().isEmpty()).orElse(false);
            System.out.println("Puzzle: " + puzzle.substring(0, 20) + "...");
            System.out.println("  Empty after simpler: " + empty + ", Y-Wing fires: " + fires + ", has elims: " + hasElims);
            if (fires && hasElims && empty > 0) {
                System.out.println("  *** VALID Y-WING PUZZLE ***");
                printGrid(puzzle);
            }
        }
    }

    // ---- helpers ----

    private Board rawBoard(List<List<Integer>> grid) {
        Board board = Board.fromGrid(grid);
        board.calculateAllCandidates();
        return board;
    }

    private List<List<Integer>> fromString(String s) {
        List<List<Integer>> grid = new ArrayList<>();
        for (int r = 0; r < 9; r++) {
            List<Integer> row = new ArrayList<>();
            for (int c = 0; c < 9; c++) {
                char ch = s.charAt(r * 9 + c);
                row.add(ch == '.' ? 0 : (ch - '0'));
            }
            grid.add(List.copyOf(row));
        }
        return List.copyOf(grid);
    }

    private void printGrid(String s) {
        for (int r = 0; r < 9; r++) {
            StringBuilder sb = new StringBuilder("  [");
            for (int c = 0; c < 9; c++) {
                if (c > 0) sb.append(", ");
                sb.append(s.charAt(r * 9 + c));
            }
            sb.append("]");
            System.out.println(sb);
        }
    }

    private Board autocompleteToBoard(List<List<Integer>> grid, HintStrategy... simpler) {
        int[][] work = to2dArray(grid);
        Board board = Board.fromGrid(toImmutableList(work));
        board.calculateAllCandidates();
        boolean progress = true;
        while (progress) {
            progress = false;
            for (var strategy : simpler) {
                Optional<HintResponse> hint = strategy.evaluate(board);
                if (hint.isPresent()) {
                    HintResponse h = hint.get();
                    boolean changed = false;
                    if (h.solvedCells() != null && !h.solvedCells().isEmpty()) {
                        for (ActionableCell cell : h.solvedCells()) {
                            work[cell.row()][cell.col()] = cell.value();
                            board.getCell(cell.row(), cell.col()).setValue(cell.value());
                            changed = true;
                        }
                        board.calculateAllCandidates();
                    }
                    if (h.eliminatedCandidates() != null && !h.eliminatedCandidates().isEmpty()) {
                        for (CandidateElimination elim : h.eliminatedCandidates()) {
                            board.getCell(elim.row(), elim.col()).removeCandidate(elim.value());
                            changed = true;
                        }
                    }
                    if (changed) { progress = true; break; }
                }
            }
        }
        return board;
    }

    private long countEmpty(Board board) {
        long count = 0;
        for (int r = 0; r < 9; r++)
            for (var c : board.getRow(r))
                if (c.isEmpty()) count++;
        return count;
    }

    private int[][] to2dArray(List<List<Integer>> grid) {
        int[][] arr = new int[9][9];
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++)
                arr[r][c] = grid.get(r).get(c);
        return arr;
    }

    private List<List<Integer>> toImmutableList(int[][] arr) {
        List<List<Integer>> result = new ArrayList<>(9);
        for (int r = 0; r < 9; r++) {
            List<Integer> row = new ArrayList<>();
            for (int c = 0; c < 9; c++) row.add(arr[r][c]);
            result.add(List.copyOf(row));
        }
        return List.copyOf(result);
    }
}
