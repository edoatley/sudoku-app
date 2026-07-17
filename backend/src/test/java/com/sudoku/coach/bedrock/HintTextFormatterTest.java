package com.sudoku.coach.bedrock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// @spec SC-BE-030 — mirrors ui/src/utils/hintDisplay.test.js's coverage of formatHintText()
class HintTextFormatterTest {

    @Test
    void format_returnsNullUnchanged() {
        assertNull(HintTextFormatter.format(null));
    }

    @Test
    void format_returnsEmptyStringUnchanged() {
        assertEquals("", HintTextFormatter.format(""));
    }

    @Test
    void format_returnsTextWithNoPatternsUnchanged() {
        String text = "A digit appears as a candidate in exactly one cell within a unit.";
        assertEquals(text, HintTextFormatter.format(text));
    }

    // ---- cell coordinates ----

    @Test
    void format_convertsZeroZeroToOneOne() {
        assertEquals("Cell (1, 1) must be 5.", HintTextFormatter.format("Cell (0, 0) must be 5."));
    }

    @Test
    void format_convertsZeroTwoToOneThree() {
        assertEquals("Cell (1, 3) must be 4.", HintTextFormatter.format("Cell (0, 2) must be 4."));
    }

    @Test
    void format_convertsEightEightToNineNine() {
        assertEquals("Cell (9, 9) must be 9.", HintTextFormatter.format("Cell (8, 8) must be 9."));
    }

    @Test
    void format_convertsCoordinatesWithoutSpaces() {
        assertEquals("Cells (4, 6) and (5, 7) both have only 2 and 7.",
                HintTextFormatter.format("Cells (3,5) and (4,6) both have only 2 and 7."));
    }

    @Test
    void format_convertsMultipleCoordinatePairsInOneString() {
        assertEquals("Cells (1, 2) and (1, 3) share candidates.",
                HintTextFormatter.format("Cells (0,1) and (0,2) share candidates."));
    }

    @Test
    void format_convertsYWingPivotPincerFocusText() {
        String input = "Pivot (3,4) has {2,7}; pincers (1,4) with [2, 9] and (3,7) with [7, 9] share candidate 9.";
        String expected = "Pivot (4, 5) has {2,7}; pincers (2, 5) with [2, 9] and (4, 8) with [7, 9] share candidate 9.";
        assertEquals(expected, HintTextFormatter.format(input));
    }

    // ---- single unit references ----

    @Test
    void format_convertsRowZeroToRowOne() {
        assertEquals("Row 1 has 8 of 9 cells filled.", HintTextFormatter.format("Row 0 has 8 of 9 cells filled."));
    }

    @Test
    void format_convertsLowercaseRowEightToRowNine() {
        assertEquals("row 4 has digit 5 as a candidate in only one cell.",
                HintTextFormatter.format("row 3 has digit 5 as a candidate in only one cell."));
    }

    @Test
    void format_convertsColumnZeroToColumnOne() {
        assertEquals("Column 1 has digit 7 as a candidate in only one cell.",
                HintTextFormatter.format("Column 0 has digit 7 as a candidate in only one cell."));
    }

    @Test
    void format_doesNotConvertDigitsThatAreNotUnitIndices() {
        assertEquals("Row 3 has digit 5 as a candidate in only one cell.",
                HintTextFormatter.format("Row 2 has digit 5 as a candidate in only one cell."));
    }

    // ---- multi-unit references ----

    @Test
    void format_convertsTwoValueRowsAndColumns() {
        assertEquals("Digit 4 in rows 1 and 3 is confined to columns 2 and 6.",
                HintTextFormatter.format("Digit 4 in rows 0 and 2 is confined to columns 1 and 5."));
    }

    @Test
    void format_convertsThreeValueRows() {
        assertEquals("Digit 3 in rows 1, 3 and 5 is confined to columns 2, 4 and 6.",
                HintTextFormatter.format("Digit 3 in rows 0, 2 and 4 is confined to columns 1, 3 and 5."));
    }

    @Test
    void format_convertsThreeValueColumns() {
        assertEquals("Digit 7 in columns 1, 4 and 7 is confined to rows 1, 4 and 7.",
                HintTextFormatter.format("Digit 7 in columns 0, 3 and 6 is confined to rows 0, 3 and 6."));
    }

    // ---- block references ----

    @Test
    void format_convertsBlockZeroToTopLeft() {
        assertEquals("top-left: digit 3 is confined to row 3.",
                HintTextFormatter.format("Block 0: digit 3 is confined to row 2."));
    }

    @Test
    void format_convertsBlockFourToCentre() {
        assertEquals("centre: digit 5 is confined to column 5.",
                HintTextFormatter.format("Block 4: digit 5 is confined to column 4."));
    }

    @Test
    void format_convertsBlockEightToBottomRight() {
        assertEquals("bottom-right: digit 9 is confined to row 9.",
                HintTextFormatter.format("Block 8: digit 9 is confined to row 8."));
    }

    @Test
    void format_convertsLowercaseBlockTwoToTopRight() {
        assertEquals("top-right contains the hint.", HintTextFormatter.format("block 2 contains the hint."));
    }

    @Test
    void format_convertsBlockReferenceInPointingPairRevealText() {
        assertEquals("Digit 3 can be removed from the rest of row 3 outside top-left.",
                HintTextFormatter.format("Digit 3 can be removed from the rest of row 2 outside block 0."));
    }

    // ---- no mutation of non-matching content ----

    @Test
    void format_doesNotAlterCandidateDigitLists() {
        assertEquals("Pivot (1, 1) has {2,7}.", HintTextFormatter.format("Pivot (0,0) has {2,7}."));
    }
}
