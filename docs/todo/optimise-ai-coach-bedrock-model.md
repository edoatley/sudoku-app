# Optimise AI Coach Bedrock Model Selection

**Summary:** Evaluate whether Claude Haiku 4.5 (current default) is the right cost/quality tradeoff for the coach, using real production token-usage logs to measure prompt-cache hit rate and latency.

**Branch context:** `rc-ai-coach` — AI coach feature using Bedrock/Claude, recently deployed and verified working.

## Why deferred

Out of scope for the initial fix (CORS + deployment). The model ID is already Haiku 4.5 but has never been benchmarked against real traffic. No COACH_BEDROCK_MODEL_ID override exists in Terraform, so the default in `application.properties` is what runs in production.

## Context

**Relevant files:**
- `backend/src/main/resources/application.properties` line 60 — `coach.bedrock.model-id` defaults to `eu.anthropic.claude-haiku-4-5-20251001-v1:0`
- `backend/src/main/java/com/sudoku/puzzle/BedrockCoachClient.java` — emits `COACH_REQUEST` / `COACH_RESPONSE` structured JSON logs with `inputTokens`, `outputTokens`, `cacheReadTokens`, `cacheWriteTokens`, `latencyMs`, `fallback`
- `scripts/logs/download-coach-logs.sh` — downloads those logs from CloudWatch as NDJSON
- `infra/lambda.tf` — Lambda env vars; add `COACH_BEDROCK_MODEL_ID` here to override per workspace

**Current state:**
The coach uses Claude Haiku 4.5 via a cross-region inference profile (`eu.anthropic.claude-haiku-4-5-20251001-v1:0`). The system prompt is ~2,000+ tokens, intentionally sized to exceed the 2,048-token threshold required for prompt caching on Haiku. Structured logs are emitted per call but no analysis has been done yet — cache hit rate, actual latency distribution, and fallback rate are all unknown in production.

**Key constraints:**
- Prompt caching on Haiku requires ≥2,048 tokens in the cached block; the system prompt is sized with this in mind — don't shrink it below that threshold
- The cross-region inference profile prefix (`eu.`) is required for eu-west-2; plain model IDs won't work
- `BEDROCK_TIMEOUT_SECONDS = 6` in `BedrockCoachClient.java` — any model swap must stay well within this budget
- Sonnet 4.6 cross-region prefix: `eu.anthropic.claude-sonnet-4-6` (check Bedrock console for exact version string)

## What to do

Two complementary measurement paths exist. Steps 1–2 below (production log analysis) were
the original plan and remain valid but have not been executed yet. A second, local path is
now available and cheaper to run first: the `ui/tests/coach-quality/` diagnostic runner
(added in PR #127/#130) replays 8 fixed scenarios against real Bedrock and already logs
`fallback`, `latencyMs`, and token counts per turn — the same signal step 2 would extract
from production logs, just from controlled local runs instead of live traffic. Doing the
scripted-scenario comparison first is quick and doesn't require waiting for real
`rc-ai-coach` traffic to accumulate; production log analysis is still worth doing afterwards
to validate the scripted result against real usage patterns before finalizing a model choice
— don't treat the scripted run alone as satisfying the acceptance criteria below.

**Scripted-scenario comparison (local, do this first):**
1. `docker-compose.coach-quality.yml`'s `backend.environment` forwards `COACH_BEDROCK_MODEL_ID`
   from the host shell (bare-key passthrough — omitted from the container entirely if unset,
   so baseline runs are unaffected). Run `scripts/local/coach-quality-repeat.sh` once per
   model to compare — it brings the stack up once, loops `npm run test:coach-quality` `RUNS`
   times (default 10, matching the precedent in `bedrock-coach-prompt-quality-findings.md`)
   against the already-running stack, tears down, moves the new reports into a labelled
   subdirectory, and prints/saves an aggregate summary — all in one command:
   ```
   AWS_PROFILE=sandbox RUNS=10 LABEL=haiku-4-5-baseline \
     bash scripts/local/coach-quality-repeat.sh
   AWS_PROFILE=sandbox COACH_BEDROCK_MODEL_ID=eu.anthropic.claude-sonnet-4-6-... \
     RUNS=10 LABEL=sonnet-4-6 bash scripts/local/coach-quality-repeat.sh
   ```
2. Compute cost estimate from the aggregated token totals in each label's `summary.txt`
   (`inputTokens * $/MTok + outputTokens * $/MTok`, adjusted for cache read/write pricing) —
   Haiku 4.5 rates below; Sonnet 4.6 rates need sourcing from the Bedrock console at run time
   (not in this repo).

**Production log analysis (do second, to validate):**
3. Collect at least 20–30 coach interactions via the UI on `rc-ai-coach`, then run `bash scripts/logs/download-coach-logs.sh --workspace rc-ai-coach --hours 72 --output /tmp/coach-logs.ndjson`
4. Analyse the NDJSON: compute mean `latencyMs`, cache hit rate (`cacheReadTokens / inputTokens`), fallback rate, and cost estimate (`inputTokens * $0.80/MTok + outputTokens * $4/MTok` for Haiku 4.5; adjust for current pricing)
5. If cache hit rate is low (< 50%) or latency is high (> 3 s p90), investigate why — likely the SnapStart snapshot doesn't persist the Bedrock client connection across invocations
6. To trial a different model (e.g. Sonnet 4.6 for quality, or a newer Haiku) in production, add `COACH_BEDROCK_MODEL_ID = "eu.anthropic.claude-sonnet-4-6-..."` to the `environment_variables` block in `infra/lambda.tf` for the rc workspace only
7. Update `application.properties` default and remove the env var override once a winner is chosen

## Model comparison results

_Not yet run. Fill in after executing the scripted-scenario comparison above._

## Acceptance criteria

- [ ] Cache hit rate measured from real logs (target ≥ 60% after first cold call)
- [ ] P90 latency measured and within 4 s for the chosen model
- [ ] Cost per 1,000 coach interactions estimated and documented
- [ ] Model choice recorded with rationale in `docs/llds/` AI coach section

## Related specs / docs

- [`docs/llds/`](../llds/) — AI coach LLD (check for `coach.bedrock.model-id` section)
- `BedrockCoachClient.java` static fields `BEDROCK_TIMEOUT_SECONDS` and `MAX_TOKENS` govern the runtime budget
