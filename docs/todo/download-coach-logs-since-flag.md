# Add --since Flag to download-coach-logs Script

**Summary:** Add a `--since` flag accepting an absolute date/timestamp so the script can filter logs from a specific point in time, not just a relative window.

**Branch context:** `rc-ai-coach` — AI coach interaction logging feature being built out

## Why deferred

Out of scope for the current PR; the `--hours` flag covers immediate needs but an absolute date filter is more useful for post-incident review or comparing across days.

## Context

**Relevant files:**
- `scripts/logs/download-coach-logs.sh` — the script to modify; currently accepts `--hours <n>` which converts to a `START_TIME` epoch-millisecond value passed to `aws logs filter-log-events --start-time`

**Current state:**
The script computes `START_TIME` as `(now - HOURS * 3600) * 1000` and always filters relative to the current moment. There is no way to say "give me logs from 2026-07-01 onwards" without doing the epoch arithmetic manually. The `aws logs filter-log-events` command accepts `--start-time` as epoch milliseconds and optionally `--end-time`, so the plumbing is already in place.

**Key constraints:**
- `--since` and `--hours` should be mutually exclusive; error if both are provided
- Input format should be flexible — at minimum accept `YYYY-MM-DD` (treated as midnight UTC) and `YYYY-MM-DDTHH:MM:SSZ`; use `date -d` (GNU) or `date -j -f` (macOS) for parsing
- macOS `date` and GNU `date` have different flags; the script uses `date +%s` which works on both, but parsing an arbitrary string requires a compatibility shim
- No new dependencies — pure bash + coreutils

## What to do

1. Add `SINCE=""` alongside the existing `HOURS=24` default at the top of the script.
2. Add a `--since` case to the `while` argument parser: `--since) SINCE="$2"; shift 2 ;;`
3. After argument parsing, validate mutual exclusivity: if both `--since` and `--hours` are set, print an error and exit 1.
4. Replace the single `START_TIME=...` line with a block that branches on whether `SINCE` is set:
   - If `SINCE` is non-empty, parse it to epoch seconds using a portable shim (try `date -d "$SINCE" +%s` first; fall back to `date -j -f "%Y-%m-%d" "$SINCE" +%s` for macOS).
   - Multiply by 1000 for milliseconds.
   - Update the status message on stderr to say `since ${SINCE}` instead of `last ${HOURS}h`.
5. Update the usage comment at the top of the file to document `--since <date>`.

## Acceptance criteria

- [ ] `--since 2026-07-01` fetches logs from midnight UTC 1 July 2026 to now
- [ ] `--since 2026-07-01T06:00:00Z` fetches logs from 06:00 UTC on that date
- [ ] `--since` and `--hours` together print an error and exit non-zero
- [ ] `--hours` alone still works as before
- [ ] Script runs correctly on both macOS and Linux (CI)

## Related specs / docs

- No existing EARS spec covers this script — it is a developer tooling convenience, not a product feature
