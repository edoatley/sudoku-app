import { describe, expect, it } from 'vitest';
import { parseStructuredLine } from './logParse.js';

describe('parseStructuredLine', () => {
  it('extracts the JSON object from a prefixed Quarkus log line', () => {
    const line =
      '2026-08-25 09:47:36,485 INFO  [com.sudoku.coach.vertex.VertexCoachClient] (executor-thread-1) ' +
      '{"type":"COACH_RESPONSE","pid":"g1","fallback":false}';
    expect(parseStructuredLine(line)).toEqual({ type: 'COACH_RESPONSE', pid: 'g1', fallback: false });
  });

  it('parses a bare JSON line (no prefix)', () => {
    expect(parseStructuredLine('{"type":"COACH_REQUEST","pid":"g2"}')).toEqual({
      type: 'COACH_REQUEST',
      pid: 'g2',
    });
  });

  it('returns null for a line with no JSON object', () => {
    expect(parseStructuredLine('2026-08-25 INFO plain text with no brace')).toBeNull();
    expect(parseStructuredLine('')).toBeNull();
  });

  it('returns null for a non-JSON fragment after the first brace (e.g. a stack trace)', () => {
    expect(parseStructuredLine('at com.sudoku.Foo.bar(Foo.java:{not json})')).toBeNull();
  });
});
