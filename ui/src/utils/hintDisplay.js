// @spec HE-UI-001, HE-UI-002, HE-UI-003, HE-UI-004, HE-UI-005

const BLOCK_NAMES = [
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

/**
 * Converts 0-based coordinates and unit indices in backend-generated hint text
 * to human-readable 1-based (cells/rows/columns) or named (blocks) equivalents.
 *
 * All transformations are pure display formatting — no internal state is mutated.
 *
 * @param {string} text - Raw hint text from the backend
 * @returns {string} Display-ready hint text
 */
export function formatHintText(text) {
  if (!text) return text;

  let result = text;

  // HE-UI-001: Cell coordinates (r, c) or (r,c) — with or without spaces, 0-indexed
  // Matches: (0,2), (0, 2), (3,8) etc. — digits 0–8 only
  result = result.replace(/\(([0-8]),\s*([0-8])\)/g, (_, r, c) => `(${Number(r) + 1}, ${Number(c) + 1})`);

  // HE-UI-004: Block references — must run before row/column to avoid double-processing
  // Matches: "Block 0", "block 3", etc.
  result = result.replace(/\b[Bb]lock ([0-8])\b/g, (_, n) => BLOCK_NAMES[Number(n)]);

  // HE-UI-003: Multi-unit references with three values: "rows 0, 2 and 4", "columns 1, 3 and 5"
  result = result.replace(
    /\b(rows|columns) ([0-8]),\s*([0-8]) and ([0-8])\b/g,
    (_, unit, a, b, c) => `${unit} ${Number(a) + 1}, ${Number(b) + 1} and ${Number(c) + 1}`
  );

  // HE-UI-003: Multi-unit references with two values: "rows 0 and 2", "columns 1 and 5"
  result = result.replace(
    /\b(rows|columns) ([0-8]) and ([0-8])\b/g,
    (_, unit, a, b) => `${unit} ${Number(a) + 1} and ${Number(b) + 1}`
  );

  // HE-UI-002: Single unit references: "Row 0", "row 3", "Column 5", "column 8"
  result = result.replace(/\b([Rr]ow|[Cc]olumn) ([0-8])\b/g, (_, unit, n) => `${unit} ${Number(n) + 1}`);

  return result;
}
