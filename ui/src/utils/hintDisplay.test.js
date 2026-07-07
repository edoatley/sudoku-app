// @spec HE-UI-001, HE-UI-002, HE-UI-003, HE-UI-004, HE-UI-005
import { describe, it, expect } from 'vitest';
import { formatHintText } from './hintDisplay.js';

describe('formatHintText', () => {
  describe('HE-UI-005: passthrough for null/empty', () => {
    it('returns null unchanged', () => {
      expect(formatHintText(null)).toBeNull();
    });
    it('returns empty string unchanged', () => {
      expect(formatHintText('')).toBe('');
    });
    it('returns text with no patterns unchanged', () => {
      expect(formatHintText('A digit appears as a candidate in exactly one cell within a unit.')).toBe(
        'A digit appears as a candidate in exactly one cell within a unit.'
      );
    });
  });

  describe('HE-UI-001: cell coordinates', () => {
    it('converts (0, 0) to (1, 1)', () => {
      expect(formatHintText('Cell (0, 0) must be 5.')).toBe('Cell (1, 1) must be 5.');
    });
    it('converts (0, 2) to (1, 3)', () => {
      expect(formatHintText('Cell (0, 2) must be 4.')).toBe('Cell (1, 3) must be 4.');
    });
    it('converts (8, 8) to (9, 9)', () => {
      expect(formatHintText('Cell (8, 8) must be 9.')).toBe('Cell (9, 9) must be 9.');
    });
    it('converts coordinates without spaces: (3,5)', () => {
      expect(formatHintText('Cells (3,5) and (4,6) both have only 2 and 7.')).toBe(
        'Cells (4, 6) and (5, 7) both have only 2 and 7.'
      );
    });
    it('converts multiple coordinate pairs in one string', () => {
      expect(formatHintText('Cells (0,1) and (0,2) share candidates.')).toBe(
        'Cells (1, 2) and (1, 3) share candidates.'
      );
    });
    it('converts FullHouseStrategy reveal text', () => {
      expect(formatHintText('Cell (2, 5) must be 8.')).toBe('Cell (3, 6) must be 8.');
    });
    it('converts NakedSingleStrategy focus text', () => {
      expect(formatHintText('Cell (0, 2) has had every other digit eliminated.')).toBe(
        'Cell (1, 3) has had every other digit eliminated.'
      );
    });
    it('converts YWingStrategy pivot/pincer focus text', () => {
      const input = 'Pivot (3,4) has {2,7}; pincers (1,4) with [2, 9] and (3,7) with [7, 9] share candidate 9.';
      const expected = 'Pivot (4, 5) has {2,7}; pincers (2, 5) with [2, 9] and (4, 8) with [7, 9] share candidate 9.';
      expect(formatHintText(input)).toBe(expected);
    });
  });

  describe('HE-UI-002: single unit references', () => {
    it('converts "Row 0" to "Row 1"', () => {
      expect(formatHintText('Row 0 has 8 of 9 cells filled.')).toBe('Row 1 has 8 of 9 cells filled.');
    });
    it('converts "row 3" to "row 4"', () => {
      expect(formatHintText('row 3 has digit 5 as a candidate in only one cell.')).toBe(
        'row 4 has digit 5 as a candidate in only one cell.'
      );
    });
    it('converts "Column 0" to "Column 1"', () => {
      expect(formatHintText('Column 0 has digit 7 as a candidate in only one cell.')).toBe(
        'Column 1 has digit 7 as a candidate in only one cell.'
      );
    });
    it('converts "column 8" to "column 9"', () => {
      expect(formatHintText('column 8 has digit 1 as a candidate in only one cell.')).toBe(
        'column 9 has digit 1 as a candidate in only one cell.'
      );
    });
    it('converts "Row 8" to "Row 9"', () => {
      expect(formatHintText('Row 8 has 8 of 9 cells filled.')).toBe('Row 9 has 8 of 9 cells filled.');
    });
    it('does not convert digits that are not unit indices (e.g. "digit 5")', () => {
      expect(formatHintText('Row 2 has digit 5 as a candidate in only one cell.')).toBe(
        'Row 3 has digit 5 as a candidate in only one cell.'
      );
    });
  });

  describe('HE-UI-003: multi-unit references', () => {
    it('converts "rows 0 and 2" to "rows 1 and 3"', () => {
      expect(formatHintText('Digit 4 in rows 0 and 2 is confined to columns 1 and 5.')).toBe(
        'Digit 4 in rows 1 and 3 is confined to columns 2 and 6.'
      );
    });
    it('converts "columns 1 and 5" to "columns 2 and 6"', () => {
      expect(formatHintText('Digit 4 can be removed from all other cells in columns 1 and 5.')).toBe(
        'Digit 4 can be removed from all other cells in columns 2 and 6.'
      );
    });
    it('converts three-value rows: "rows 0, 2 and 4" to "rows 1, 3 and 5"', () => {
      expect(formatHintText('Digit 3 in rows 0, 2 and 4 is confined to columns 1, 3 and 5.')).toBe(
        'Digit 3 in rows 1, 3 and 5 is confined to columns 2, 4 and 6.'
      );
    });
    it('converts three-value columns: "columns 0, 3 and 6" to "columns 1, 4 and 7"', () => {
      expect(formatHintText('Digit 7 in columns 0, 3 and 6 is confined to rows 0, 3 and 6.')).toBe(
        'Digit 7 in columns 1, 4 and 7 is confined to rows 1, 4 and 7.'
      );
    });
  });

  describe('HE-UI-004: block references', () => {
    it('converts "Block 0" to "top-left"', () => {
      expect(formatHintText('Block 0: digit 3 is confined to row 2.')).toBe('top-left: digit 3 is confined to row 3.');
    });
    it('converts "Block 4" to "centre"', () => {
      expect(formatHintText('Block 4: digit 5 is confined to column 4.')).toBe(
        'centre: digit 5 is confined to column 5.'
      );
    });
    it('converts "Block 8" to "bottom-right"', () => {
      expect(formatHintText('Block 8: digit 9 is confined to row 8.')).toBe(
        'bottom-right: digit 9 is confined to row 9.'
      );
    });
    it('converts lowercase "block 2" to "top-right"', () => {
      expect(formatHintText('block 2 contains the hint.')).toBe('top-right contains the hint.');
    });
    it('converts all nine block names correctly', () => {
      const names = [
        'top-left',
        'top-middle',
        'top-right',
        'middle-left',
        'centre',
        'middle-right',
        'bottom-left',
        'bottom-middle',
        'bottom-right',
      ];
      names.forEach((name, i) => {
        expect(formatHintText(`Block ${i} test`)).toBe(`${name} test`);
      });
    });
    it('converts block reference in PointingPair reveal text', () => {
      expect(formatHintText('Digit 3 can be removed from the rest of row 2 outside block 0.')).toBe(
        'Digit 3 can be removed from the rest of row 3 outside top-left.'
      );
    });
  });

  describe('HE-UI-005: no mutation of non-matching content', () => {
    it('does not alter digit values that are not coordinates or unit indices', () => {
      expect(formatHintText('Cell (1, 2) must be 9.')).toBe('Cell (2, 3) must be 9.');
    });
    it('does not alter candidate digit lists like {2,7}', () => {
      expect(formatHintText('Pivot (0,0) has {2,7}.')).toBe('Pivot (1, 1) has {2,7}.');
    });
  });
});
