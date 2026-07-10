import { useCallback, useRef } from 'react';

// Cap the in-memory buffer so a long session cannot grow it without bound.
const MAX_EVENTS = 500;

/**
 * In-memory buffer of puzzle-play actions (NUMBER, NUMBER_CLEAR, HINT_*) that are flushed to
 * the backend on the existing game sync for observability — see
 * docs/llds/react-frontend.md (Puzzle-Play Event Buffer). Observability-only: losing the buffer
 * (e.g. a hard crash before a flush) degrades to missing log lines and never affects gameplay.
 *
 * @spec FE-BE-020, FE-BE-022, FE-BE-023, FE-BE-024
 */
export function useEventLog() {
  const bufferRef = useRef([]);
  const truncatedRef = useRef(false);

  // Append an action with a client timestamp; drop-oldest and flag truncation past the cap.
  const recordEvent = useCallback((event) => {
    const buf = bufferRef.current;
    buf.push({ ...event, clientTs: Date.now() });
    if (buf.length > MAX_EVENTS) {
      buf.splice(0, buf.length - MAX_EVENTS);
      truncatedRef.current = true;
    }
  }, []);

  // Remove and return the current batch (with an EVENTS_TRUNCATED marker prepended when the
  // buffer overflowed). New events accumulate in a fresh buffer while this batch is in flight.
  const takeBatch = useCallback(() => {
    const events = bufferRef.current;
    const truncated = truncatedRef.current;
    if (events.length === 0 && !truncated) return null;
    bufferRef.current = [];
    truncatedRef.current = false;
    const wire = truncated ? [{ type: 'EVENTS_TRUNCATED', clientTs: Date.now() }, ...events] : events;
    return { events, truncated, wire };
  }, []);

  // Put a taken batch back at the front of the buffer after a failed flush, so it retries next sync.
  const restoreBatch = useCallback((batch) => {
    if (!batch) return;
    bufferRef.current = [...batch.events, ...bufferRef.current];
    if (batch.truncated) truncatedRef.current = true;
  }, []);

  // Drop everything — called on game start/finish so events never attach to the wrong game.
  const resetEvents = useCallback(() => {
    bufferRef.current = [];
    truncatedRef.current = false;
  }, []);

  return { recordEvent, takeBatch, restoreBatch, resetEvents };
}

// Correlation id pairing a HINT_REQUEST with its HINT_RESPONSE.
export function newCid() {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
