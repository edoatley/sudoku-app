import { useEffect } from 'react';

// @spec KBD-001, KBD-002, KBD-003, KBD-010, KBD-011, KBD-012, KBD-013,
//       KBD-014, KBD-015, KBD-020, KBD-021, KBD-022, KBD-030, KBD-031,
//       KBD-032, KBD-033, KBD-034, KBD-041
export function useKeyboardInput({
  selectedCell,
  inputMode,
  originalGrid,
  currentGrid,
  gameStatus,
  isPaused,
  isModalOpen,
  onDigit,
  onClear,
  onSelectCell,
  onToggleMode,
}) {
  useEffect(() => {
    function handleKeyDown(e) {
      // @spec KBD-032, KBD-033 — suppress while any modal or hint panel is open
      if (isModalOpen) return;
      // @spec KBD-030 — suppress when no game is loaded or game is solved/complete
      if (!currentGrid || gameStatus === 'solved') return;
      // @spec KBD-031 — suppress while paused
      if (isPaused) return;

      // @spec KBD-041 — Space toggles normal/candidate mode (works without a selected cell)
      if (e.key === ' ') {
        e.preventDefault();
        onToggleMode(inputMode === 'normal' ? 'candidate' : 'normal');
        return;
      }

      // @spec KBD-022 — Escape deselects (works without a selected cell)
      if (e.key === 'Escape') {
        onSelectCell(null);
        return;
      }

      // @spec KBD-034 — all remaining actions require a selected cell
      if (!selectedCell) return;
      const { row, col } = selectedCell;

      // @spec KBD-010, KBD-011, KBD-012, KBD-013, KBD-014 — arrow navigation
      const moves = {
        ArrowUp:    { row: Math.max(0, row - 1), col },
        ArrowDown:  { row: Math.min(8, row + 1), col },
        ArrowLeft:  { row, col: Math.max(0, col - 1) },
        ArrowRight: { row, col: Math.min(8, col + 1) },
      };
      if (moves[e.key]) {
        e.preventDefault();
        onSelectCell(moves[e.key]);
        return;
      }

      // @spec KBD-003 — block writes to given cells
      if (originalGrid[row][col] !== 0) return;

      // @spec KBD-001, KBD-002 — digit entry (1–9)
      const digit = parseInt(e.key, 10);
      if (digit >= 1 && digit <= 9) {
        onDigit(row, col, digit);
        return;
      }

      // @spec KBD-020, KBD-021 — clear: 0, Delete, or Backspace
      if (e.key === '0' || e.key === 'Delete' || e.key === 'Backspace') {
        e.preventDefault();
        onClear(row, col);
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [selectedCell, inputMode, originalGrid, currentGrid, gameStatus, isPaused,
      isModalOpen, onDigit, onClear, onSelectCell, onToggleMode]);
}
