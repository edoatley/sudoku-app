# Fix the minute-boundary flake in FirestoreCoachRateLimiterTest

**Summary:** Both tests in `FirestoreCoachRateLimiterTest` read the wall clock through the
production code path, so a run that straddles a UTC minute boundary fails spuriously — the
counter resets mid-test and a call expected to be rejected is allowed.

**Branch context:** `rcg-p6v2` — GCP Pulumi re-platform Phase 6. Surfaced in a pre-push run of
`scripts/local/local-alltests.sh`; nothing on that branch touches `backend/`.

## Why deferred

Found off-task during Phase 6's pre-push gate and unrelated to it, so it belongs on its own
branch rather than being bundled into an infrastructure PR.

## Context

**Relevant files:**
- `backend/src/test/java/com/sudoku/coach/bedrock/FirestoreCoachRateLimiterTest.java` — the two
  flaky tests; already sets private fields by reflection in `setUp`
- `backend/src/main/java/com/sudoku/coach/bedrock/FirestoreCoachRateLimiter.java` — reads
  `now` internally (line ~49) and keys the counter document `userId__<window>`
- `backend/src/test/java/com/sudoku/game/persistence/FirestoreEmulatorProfile.java` — the test
  profile pointing at the Dev Services Firestore emulator

**Current state:**
`tryConsume` formats the current UTC minute into `window` and reads/writes the Firestore document
`userId__window`, with a TTL two minutes past the window start. The counter is therefore scoped to
whichever minute each individual call happens to land in. `limitIsPerUser` asserts that user `a`'s
third call is rejected; when calls two and three fall either side of a minute boundary the third
lands in a fresh window with a zero count and is allowed, failing with
`user a exhausted ==> expected: <false> but was: <true>`.
`allowsUpToLimitThenRejectsWithinSameWindow` makes four calls and is vulnerable the same way.

**Observed rate:** failed once, then passed in isolation and in two subsequent full-suite runs —
consistent with a boundary race rather than shared state between tests. Both tests already use a
fresh `UUID` per user, so cross-test pollution is ruled out.

**Key constraints:**
- The per-UTC-minute window is the specified behaviour (`SC-RL-003`); do not widen it to fix the
  test.
- Keep the emulator-backed test — it is the only coverage of the real Firestore read/write path.

## What to do

1. Add a `java.time.Clock` field to `FirestoreCoachRateLimiter`, defaulting to `Clock.systemUTC()`,
   and derive `now` from it instead of calling the clock directly.
2. In `FirestoreCoachRateLimiterTest.setUp`, pin it with `set("clock", Clock.fixed(...))` using the
   reflection helper already present, so every call in a test shares one window.
3. Confirm no production behaviour changed: the default clock keeps the UTC-minute window intact.

If a `Clock` field is unwelcome in the adapter, the alternative is to have the test detect a
boundary crossing and retry, but that leaves the race in place and is strictly worse.

## Acceptance criteria

- [ ] `./mvnw -B -ntp test -Dtest=FirestoreCoachRateLimiterTest` passes with a fixed clock pinned
      to one second before a minute boundary — the case that currently fails
- [ ] Full `./mvnw -B -ntp verify` green
- [ ] `FirestoreCoachRateLimiter` still keys per UTC minute when no clock is injected

## Related specs / docs

- [`docs/specs/sudoku-coach-specs.md`](../specs/sudoku-coach-specs.md) — `SC-RL-003` (429 when the
  per-minute limit is exceeded), `SC-RL-004`
