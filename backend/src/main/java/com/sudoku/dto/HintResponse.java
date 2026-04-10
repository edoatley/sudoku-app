package com.sudoku.dto;

import java.util.List;

public record HintResponse(
        String techniqueName,
        String markdownSlug,
        String difficulty,
        int strategyRank,
        String nudge,
        String focus,
        String reveal,
        List<Coordinate> highlightCells,
        List<CandidateElimination> eliminatedCandidates,
        List<ActionableCell> solvedCells) {}
