package com.sudoku.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// @spec SL-DATA-006, SL-DATA-007, SL-DATA-008
class CellTest {

    // @spec SL-DATA-006
    @Test
    void constructorSetsFieldsCorrectly() {
        Cell cell = new Cell(3, 5, 7);
        assertEquals(3, cell.row());
        assertEquals(5, cell.col());
        assertEquals(7, cell.value());
        assertFalse(cell.isEmpty());
        assertTrue(cell.candidates().isEmpty());
    }

    // @spec SL-DATA-008
    @Test
    void isEmptyTrueForValueZero() {
        Cell cell = new Cell(0, 0, 0);
        assertTrue(cell.isEmpty());
    }

    // @spec SL-DATA-001
    @Test
    void constructorRejectsInvalidRow() {
        assertThrows(IllegalArgumentException.class, () -> new Cell(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cell(9, 0, 0));
    }

    // @spec SL-DATA-001
    @Test
    void constructorRejectsInvalidCol() {
        assertThrows(IllegalArgumentException.class, () -> new Cell(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cell(0, 9, 0));
    }

    // @spec SL-DATA-002
    @Test
    void constructorRejectsInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> new Cell(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Cell(0, 0, 10));
    }

    // @spec SL-DATA-006
    @Test
    void setCandidatesAndRetrieve() {
        Cell cell = new Cell(0, 0, 0);
        cell.setCandidates(Set.of(1, 3, 7));
        assertEquals(Set.of(1, 3, 7), cell.candidates());
    }

    // @spec SL-DATA-006
    @Test
    void candidatesIsUnmodifiable() {
        Cell cell = new Cell(0, 0, 0);
        cell.setCandidates(Set.of(2, 5));
        assertThrows(UnsupportedOperationException.class, () -> cell.candidates().add(9));
    }

    // @spec SL-DATA-006
    @Test
    void removeCandidateRemovesDigit() {
        Cell cell = new Cell(0, 0, 0);
        cell.setCandidates(Set.of(1, 2, 3));
        cell.removeCandidate(2);
        assertEquals(Set.of(1, 3), cell.candidates());
    }

    // @spec SL-DATA-007
    @Test
    void setValueClearsCandidates() {
        Cell cell = new Cell(0, 0, 0);
        cell.setCandidates(Set.of(4, 5, 6));
        cell.setValue(4);
        assertEquals(4, cell.value());
        assertFalse(cell.isEmpty());
        assertTrue(cell.candidates().isEmpty());
    }

    // @spec SL-DATA-002
    @Test
    void setValueRejectsInvalidValue() {
        Cell cell = new Cell(0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> cell.setValue(10));
        assertThrows(IllegalArgumentException.class, () -> cell.setValue(-1));
    }

    // @spec SL-DATA-006
    @Test
    void setCandidatesDefensivelyCopies() {
        Cell cell = new Cell(0, 0, 0);
        java.util.HashSet<Integer> original = new java.util.HashSet<>(Set.of(1, 2, 3));
        cell.setCandidates(original);
        original.add(9);
        assertFalse(cell.candidates().contains(9));
    }
}
