// @spec DT-UI-001, DT-UI-002, DT-UI-003, DT-UI-004

/**
 * Convert a wire-format grid `{rows: [[...],...]}` to a plain array-of-arrays.
 * Returns null if wireGrid is null or undefined.
 */
export function gridFromWire(wireGrid) {
  if (wireGrid == null) return null;
  return wireGrid.rows;
}

/**
 * Convert a plain array-of-arrays to wire format `{rows: [[...],...]}`.
 * Returns null if grid is null or undefined.
 */
export function gridToWire(grid) {
  if (grid == null) return null;
  return { rows: grid };
}

/**
 * Convert a wire-format candidates grid `{rows: [[[...],...],...]}`
 * to a plain array-of-arrays-of-arrays.
 * Returns null if wireCandidates is null or undefined.
 */
export function candidatesFromWire(wireCandidates) {
  if (wireCandidates == null) return null;
  return wireCandidates.rows;
}

/**
 * Convert a plain array-of-arrays-of-arrays to wire format
 * `{rows: [[[...],...],...]}`
 * Returns null if candidates is null or undefined.
 */
export function candidatesToWire(candidates) {
  if (candidates == null) return null;
  return { rows: candidates };
}
