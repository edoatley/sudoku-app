import { describe, it, expect, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { fireEvent } from '@testing-library/dom';
import { useKeyboardInput } from './useKeyboardInput.js';

// ─── Helpers ──────────────────────────────────────────────────────────────────

const ORIGINAL_GRID = Array(9)
  .fill(null)
  .map(
    (_, r) =>
      Array(9)
        .fill(null)
        .map((_, c) => (r === 0 && c === 0 ? 5 : 0)) // (0,0) is a given
  );

function makeProps(overrides = {}) {
  return {
    selectedCell: { row: 1, col: 1 },
    inputMode: 'normal',
    originalGrid: ORIGINAL_GRID,
    currentGrid: ORIGINAL_GRID,
    gameStatus: 'idle',
    isPaused: false,
    isModalOpen: false,
    onDigit: vi.fn(),
    onClear: vi.fn(),
    onSelectCell: vi.fn(),
    onToggleMode: vi.fn(),
    onUndo: vi.fn(),
    onCheck: vi.fn(),
    onHint: vi.fn(),
    onFill: vi.fn(),
    onHelp: vi.fn(),
    onPause: vi.fn(),
    ...overrides,
  };
}

function pressKey(key) {
  fireEvent.keyDown(document, { key });
}

// ─── Digit entry ──────────────────────────────────────────────────────────────

describe('digit entry', () => {
  // @spec KBD-001
  it('calls onDigit with row, col, digit in normal mode', () => {
    const props = makeProps();
    renderHook(() => useKeyboardInput(props));
    pressKey('5');
    expect(props.onDigit).toHaveBeenCalledWith(1, 1, 5);
  });

  // @spec KBD-002
  it('calls onDigit with row, col, digit in candidate mode', () => {
    const props = makeProps({ inputMode: 'candidate' });
    renderHook(() => useKeyboardInput(props));
    pressKey('3');
    expect(props.onDigit).toHaveBeenCalledWith(1, 1, 3);
  });

  // @spec KBD-003
  it('ignores digit key on a given cell', () => {
    const props = makeProps({ selectedCell: { row: 0, col: 0 } });
    renderHook(() => useKeyboardInput(props));
    pressKey('5');
    expect(props.onDigit).not.toHaveBeenCalled();
  });
});

// ─── Clear & deselect ─────────────────────────────────────────────────────────

describe('clear and deselect', () => {
  // @spec KBD-020
  it('calls onClear on Delete', () => {
    const props = makeProps();
    renderHook(() => useKeyboardInput(props));
    pressKey('Delete');
    expect(props.onClear).toHaveBeenCalledWith(1, 1);
  });

  // @spec KBD-020
  it('calls onClear on Backspace', () => {
    const props = makeProps();
    renderHook(() => useKeyboardInput(props));
    pressKey('Backspace');
    expect(props.onClear).toHaveBeenCalledWith(1, 1);
  });

  // @spec KBD-020
  it('calls onClear on 0', () => {
    const props = makeProps();
    renderHook(() => useKeyboardInput(props));
    pressKey('0');
    expect(props.onClear).toHaveBeenCalledWith(1, 1);
  });

  // @spec KBD-022
  it('calls onSelectCell(null) on Escape', () => {
    const props = makeProps();
    renderHook(() => useKeyboardInput(props));
    pressKey('Escape');
    expect(props.onSelectCell).toHaveBeenCalledWith(null);
  });

  // @spec KBD-022
  it('calls onSelectCell(null) on Escape even with no selected cell', () => {
    const props = makeProps({ selectedCell: null });
    renderHook(() => useKeyboardInput(props));
    pressKey('Escape');
    expect(props.onSelectCell).toHaveBeenCalledWith(null);
  });
});

// ─── Arrow navigation ─────────────────────────────────────────────────────────

describe('arrow navigation', () => {
  // @spec KBD-010
  it('moves selection up', () => {
    const props = makeProps({ selectedCell: { row: 3, col: 3 } });
    renderHook(() => useKeyboardInput(props));
    pressKey('ArrowUp');
    expect(props.onSelectCell).toHaveBeenCalledWith({ row: 2, col: 3 });
  });

  // @spec KBD-010
  it('clamps at top row', () => {
    const props = makeProps({ selectedCell: { row: 0, col: 3 } });
    renderHook(() => useKeyboardInput(props));
    pressKey('ArrowUp');
    expect(props.onSelectCell).toHaveBeenCalledWith({ row: 0, col: 3 });
  });

  // @spec KBD-011
  it('moves selection down', () => {
    const props = makeProps({ selectedCell: { row: 3, col: 3 } });
    renderHook(() => useKeyboardInput(props));
    pressKey('ArrowDown');
    expect(props.onSelectCell).toHaveBeenCalledWith({ row: 4, col: 3 });
  });

  // @spec KBD-011
  it('clamps at bottom row', () => {
    const props = makeProps({ selectedCell: { row: 8, col: 3 } });
    renderHook(() => useKeyboardInput(props));
    pressKey('ArrowDown');
    expect(props.onSelectCell).toHaveBeenCalledWith({ row: 8, col: 3 });
  });

  // @spec KBD-012
  it('moves selection left', () => {
    const props = makeProps({ selectedCell: { row: 3, col: 3 } });
    renderHook(() => useKeyboardInput(props));
    pressKey('ArrowLeft');
    expect(props.onSelectCell).toHaveBeenCalledWith({ row: 3, col: 2 });
  });

  // @spec KBD-012
  it('clamps at left column', () => {
    const props = makeProps({ selectedCell: { row: 3, col: 0 } });
    renderHook(() => useKeyboardInput(props));
    pressKey('ArrowLeft');
    expect(props.onSelectCell).toHaveBeenCalledWith({ row: 3, col: 0 });
  });

  // @spec KBD-013
  it('moves selection right', () => {
    const props = makeProps({ selectedCell: { row: 3, col: 3 } });
    renderHook(() => useKeyboardInput(props));
    pressKey('ArrowRight');
    expect(props.onSelectCell).toHaveBeenCalledWith({ row: 3, col: 4 });
  });

  // @spec KBD-013
  it('clamps at right column', () => {
    const props = makeProps({ selectedCell: { row: 3, col: 8 } });
    renderHook(() => useKeyboardInput(props));
    pressKey('ArrowRight');
    expect(props.onSelectCell).toHaveBeenCalledWith({ row: 3, col: 8 });
  });

  // @spec KBD-015
  it('does not change inputMode on arrow key', () => {
    const props = makeProps({ inputMode: 'candidate' });
    renderHook(() => useKeyboardInput(props));
    pressKey('ArrowRight');
    // inputMode is not a callback — we verify onDigit/onClear are not called
    expect(props.onDigit).not.toHaveBeenCalled();
    expect(props.onClear).not.toHaveBeenCalled();
  });
});

// ─── Mode toggle ──────────────────────────────────────────────────────────────

describe('mode toggle', () => {
  // @spec KBD-041
  it('toggles from normal to candidate on Space', () => {
    const props = makeProps({ inputMode: 'normal' });
    renderHook(() => useKeyboardInput(props));
    pressKey(' ');
    expect(props.onToggleMode).toHaveBeenCalledWith('candidate');
  });

  // @spec KBD-041
  it('toggles from candidate to normal on Space', () => {
    const props = makeProps({ inputMode: 'candidate' });
    renderHook(() => useKeyboardInput(props));
    pressKey(' ');
    expect(props.onToggleMode).toHaveBeenCalledWith('normal');
  });

  // @spec KBD-041
  it('toggles mode even without a selected cell', () => {
    const props = makeProps({ selectedCell: null });
    renderHook(() => useKeyboardInput(props));
    pressKey(' ');
    expect(props.onToggleMode).toHaveBeenCalledWith('candidate');
  });

  it('does not toggle mode when paused', () => {
    const props = makeProps({ isPaused: true });
    renderHook(() => useKeyboardInput(props));
    pressKey(' ');
    expect(props.onToggleMode).not.toHaveBeenCalled();
  });
});

// ─── Guard conditions ─────────────────────────────────────────────────────────

describe('guard conditions', () => {
  // @spec KBD-030
  it('suppresses all keys when no grid is loaded', () => {
    const props = makeProps({ currentGrid: null });
    renderHook(() => useKeyboardInput(props));
    pressKey('5');
    pressKey('ArrowUp');
    pressKey('Escape');
    expect(props.onDigit).not.toHaveBeenCalled();
    expect(props.onSelectCell).not.toHaveBeenCalled();
  });

  // @spec KBD-030
  it('suppresses all keys when game is solved', () => {
    const props = makeProps({ gameStatus: 'solved' });
    renderHook(() => useKeyboardInput(props));
    pressKey('5');
    expect(props.onDigit).not.toHaveBeenCalled();
  });

  // @spec KBD-031
  it('suppresses all keys including arrows when paused', () => {
    const props = makeProps({ isPaused: true });
    renderHook(() => useKeyboardInput(props));
    pressKey('5');
    pressKey('ArrowDown');
    pressKey('Escape');
    expect(props.onDigit).not.toHaveBeenCalled();
    expect(props.onSelectCell).not.toHaveBeenCalled();
  });

  // @spec KBD-032
  it('suppresses all keys when a modal is open', () => {
    const props = makeProps({ isModalOpen: true });
    renderHook(() => useKeyboardInput(props));
    pressKey('5');
    pressKey('ArrowUp');
    pressKey('Escape');
    expect(props.onDigit).not.toHaveBeenCalled();
    expect(props.onSelectCell).not.toHaveBeenCalled();
  });

  // @spec KBD-033 — isModalOpen=true covers hint panel (derived in App.jsx)
  it('suppresses all keys when hint panel is open (isModalOpen=true)', () => {
    const props = makeProps({ isModalOpen: true });
    renderHook(() => useKeyboardInput(props));
    pressKey('3');
    expect(props.onDigit).not.toHaveBeenCalled();
  });

  // @spec KBD-034
  it('ignores digit key when no cell is selected', () => {
    const props = makeProps({ selectedCell: null });
    renderHook(() => useKeyboardInput(props));
    pressKey('5');
    expect(props.onDigit).not.toHaveBeenCalled();
  });

  // @spec KBD-034
  it('ignores clear key when no cell is selected', () => {
    const props = makeProps({ selectedCell: null });
    renderHook(() => useKeyboardInput(props));
    pressKey('Delete');
    expect(props.onClear).not.toHaveBeenCalled();
  });

  // @spec KBD-034
  it('ignores arrow key when no cell is selected', () => {
    const props = makeProps({ selectedCell: null });
    renderHook(() => useKeyboardInput(props));
    pressKey('ArrowUp');
    expect(props.onSelectCell).not.toHaveBeenCalled();
  });

  it('suppresses all keys when focus is inside an input element (e.g. coach chat)', () => {
    const props = makeProps();
    renderHook(() => useKeyboardInput(props));
    const input = document.createElement('input');
    document.body.appendChild(input);
    fireEvent.keyDown(input, { key: '5' });
    fireEvent.keyDown(input, { key: 'ArrowUp' });
    fireEvent.keyDown(input, { key: 'Delete' });
    expect(props.onDigit).not.toHaveBeenCalled();
    expect(props.onSelectCell).not.toHaveBeenCalled();
    expect(props.onClear).not.toHaveBeenCalled();
    document.body.removeChild(input);
  });

  it('suppresses all keys when focus is inside a textarea element', () => {
    const props = makeProps();
    renderHook(() => useKeyboardInput(props));
    const textarea = document.createElement('textarea');
    document.body.appendChild(textarea);
    fireEvent.keyDown(textarea, { key: '3' });
    expect(props.onDigit).not.toHaveBeenCalled();
    document.body.removeChild(textarea);
  });

  it('cleans up the event listener on unmount', () => {
    const props = makeProps();
    const { unmount } = renderHook(() => useKeyboardInput(props));
    unmount();
    pressKey('5');
    expect(props.onDigit).not.toHaveBeenCalled();
  });
});

// ─── Toolbar shortcuts ────────────────────────────────────────────────────────

describe('toolbar shortcuts', () => {
  // @spec KBD-050
  it('calls onUndo on U key', () => {
    const props = makeProps();
    renderHook(() => useKeyboardInput(props));
    pressKey('u');
    expect(props.onUndo).toHaveBeenCalled();
  });

  // @spec KBD-050
  it('calls onUndo on uppercase U key', () => {
    const props = makeProps();
    renderHook(() => useKeyboardInput(props));
    pressKey('U');
    expect(props.onUndo).toHaveBeenCalled();
  });

  // @spec KBD-051
  it('calls onCheck on C key', () => {
    const props = makeProps();
    renderHook(() => useKeyboardInput(props));
    pressKey('c');
    expect(props.onCheck).toHaveBeenCalled();
  });

  // @spec KBD-052
  it('calls onHint on H key', () => {
    const props = makeProps();
    renderHook(() => useKeyboardInput(props));
    pressKey('h');
    expect(props.onHint).toHaveBeenCalled();
  });

  // @spec KBD-053
  it('calls onFill on F key', () => {
    const props = makeProps();
    renderHook(() => useKeyboardInput(props));
    pressKey('f');
    expect(props.onFill).toHaveBeenCalled();
  });

  // @spec KBD-054
  it('calls onHelp on ? key', () => {
    const props = makeProps();
    renderHook(() => useKeyboardInput(props));
    pressKey('?');
    expect(props.onHelp).toHaveBeenCalled();
  });

  // @spec KBD-054
  it('calls onHelp on / key', () => {
    const props = makeProps();
    renderHook(() => useKeyboardInput(props));
    pressKey('/');
    expect(props.onHelp).toHaveBeenCalled();
  });

  // @spec KBD-055
  it('calls onPause on P key when not paused', () => {
    const props = makeProps({ isPaused: false });
    renderHook(() => useKeyboardInput(props));
    pressKey('p');
    expect(props.onPause).toHaveBeenCalled();
  });

  // @spec KBD-055
  it('calls onPause on P key even when game is paused', () => {
    const props = makeProps({ isPaused: true });
    renderHook(() => useKeyboardInput(props));
    pressKey('p');
    expect(props.onPause).toHaveBeenCalled();
  });

  // @spec KBD-055
  it('does not call onPause when modal is open', () => {
    const props = makeProps({ isModalOpen: true });
    renderHook(() => useKeyboardInput(props));
    pressKey('p');
    expect(props.onPause).not.toHaveBeenCalled();
  });

  it('suppresses toolbar shortcuts when paused (except p)', () => {
    const props = makeProps({ isPaused: true });
    renderHook(() => useKeyboardInput(props));
    pressKey('u');
    pressKey('c');
    pressKey('h');
    pressKey('f');
    pressKey('?');
    expect(props.onUndo).not.toHaveBeenCalled();
    expect(props.onCheck).not.toHaveBeenCalled();
    expect(props.onHint).not.toHaveBeenCalled();
    expect(props.onFill).not.toHaveBeenCalled();
    expect(props.onHelp).not.toHaveBeenCalled();
  });
});
