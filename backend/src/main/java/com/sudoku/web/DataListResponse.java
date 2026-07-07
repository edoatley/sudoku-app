package com.sudoku.web;

import java.util.List;

/**
 * Generic wrapper that returns a list of data items under a named {@code items} key.
 * Used by developer-only data browser endpoints to provide a consistent response envelope.
 *
 * @param <T> the type of each item in the list
 */
public record DataListResponse<T>(List<T> items) {}
