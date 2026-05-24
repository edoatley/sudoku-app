package com.sudoku.dto;

import java.util.List;

// @spec GH-DTO-004
public record GameHistoryResponse(List<GameHistoryEntry> entries) {}
