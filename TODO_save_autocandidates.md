# TODO: Save Auto-Candidates as User Notes

## Overview

When a user activates auto-notes mode (clicking the "Notes" button), the app fetches all valid candidates
from the API and displays them as a read-only overlay. Currently there is no way to "adopt" these
computed candidates into the editable user notes so the user can refine them manually.

**Goal:** Add a "Keep Notes" button that converts the auto-computed `autoNotesGrid` into the user's
editable `candidateGrid`, then switches to candidate editing mode so they can remove or adjust candidates
as they work through the puzzle.

---

## Current State

| Aspect | Detail |
|--------|--------|
| Auto-notes state | `autoNotesGrid` — computed by `POST /puzzles/candidates`, read-only overlay |
| User notes state | `candidateGrid` — user-entered, mutable, persisted to localStorage + backend |
| Display logic | When `autoNotesActive` is true, `autoNotesGrid` is shown instead of `candidateGrid` |
| User notes input | Toggling "Candidate" mode then clicking cells to add/remove digits |

---

## Required Changes

### 1. `ui/src/hooks/useSudokuGame.js`

Add a `keepAutoNotes()` function that:

- Deep-copies `autoNotesGrid` into `candidateGrid` (replacing any existing manual notes)
- Saves updated `candidateGrid` to localStorage (`sudoku_candidateGrid`)
- Clears `autoNotesGrid` and sets `autoNotesActive = false`
- Switches `inputMode` to `"candidate"` so the user can immediately edit
- Pushes an entry to the undo history: `{ type: 'keepAutoNotes', prevCandidateGrid: <old value> }`
- Persists the new `candidateGrid` to the backend via `PATCH /games/{gameId}` (same payload shape as existing candidate saves)

Extend the undo handler to handle `type === 'keepAutoNotes'`:

- Restore `candidateGrid` to `prevCandidateGrid`
- Optionally re-activate auto-notes (or simply leave auto-notes off and restore manual state)

**Warning condition:** If `candidateGrid` already contains any user-entered candidates, prompt the user
before overwriting (a simple `window.confirm` or a small inline confirmation UI is acceptable).

### 2. `ui/src/components/NumberPad.jsx`

Add a "Keep Notes" button that:

- Is only rendered when `autoNotesActive === true`
- Calls `keepAutoNotes()` on click
- Is visually distinct from the toggle buttons (e.g. a filled/contained variant or a different colour)
- Is positioned adjacent to the existing "Notes" button so the workflow is clear:
  *Notes → (review) → Keep Notes*

---

## UX Flow

```
User clicks "Notes"
  → API returns all valid candidates
  → autoNotesGrid displayed (read-only)
  → "Keep Notes" button appears

User reviews the candidates, then clicks "Keep Notes"
  → autoNotesGrid copied to candidateGrid
  → auto-notes deactivated
  → input mode switched to "Candidate"
  → user can now tap candidates to remove unwanted ones
  → "Keep Notes" button disappears
```

---

## No Backend Changes Required

The candidate data structure (`List<List<List<Integer>>>`) is identical for both auto-notes and user
notes. The existing `PATCH /games/{gameId}` endpoint already accepts and persists candidates. No new
API endpoints or backend logic are needed.

---

## Files to Modify

| File | Change |
|------|--------|
| `ui/src/hooks/useSudokuGame.js` | Add `keepAutoNotes()`, extend undo handler |
| `ui/src/components/NumberPad.jsx` | Add conditional "Keep Notes" button |

---

## Testing Considerations

- Activate auto-notes, click "Keep Notes" — verify `candidateGrid` matches the auto-notes that were shown
- Undo after keeping — verify `candidateGrid` reverts to its previous state
- Keep notes when manual candidates already exist — verify the warning/confirmation fires
- Save game after keeping — verify candidates are persisted correctly to DynamoDB
- Refresh page after keeping — verify candidates reload correctly from backend/localStorage
