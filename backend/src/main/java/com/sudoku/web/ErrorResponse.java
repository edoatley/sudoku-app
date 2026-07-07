package com.sudoku.web;

// @spec AEH-DTO-001, AEH-DTO-002, AEH-DTO-003, AEH-DTO-004

/**
 * Standard error envelope returned by all API error responses.
 *
 * <p>Every error at the REST boundary produces this shape:
 * {@code {"code": "...", "message": "...", "detail": "..."}}.
 * The {@code detail} field is null when no additional context is available,
 * but the {@link #detail()} accessor always returns a non-null string.
 */
public record ErrorResponse(String code, String message, String detail) {

    public static final String INVALID_GRID           = "INVALID_GRID";
    public static final String INVALID_PUZZLE         = "INVALID_PUZZLE";
    public static final String GAME_NOT_FOUND         = "GAME_NOT_FOUND";
    public static final String PLAYER_NOT_FOUND       = "PLAYER_NOT_FOUND";
    public static final String INVALID_PLAYER_UPDATE  = "INVALID_PLAYER_UPDATE";
    public static final String INTERNAL_ERROR         = "INTERNAL_ERROR";

    /** Convenience constructor — no additional detail to surface. */
    public ErrorResponse(String code, String message) {
        this(code, message, null);
    }

    /** Safe accessor — never returns null; returns empty string when detail was not set. */
    @Override
    public String detail() {
        return detail != null ? detail : "";
    }
}
