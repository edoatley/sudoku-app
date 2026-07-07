package com.sudoku.game.web;

import java.util.List;

// @spec GH-DTO-004
public record GameHistoryResponse(List<GameHistoryEntry> entries) {}
