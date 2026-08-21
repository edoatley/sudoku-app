# Resume roadmap Sequence item 3 — spec-annotation backfill

**Summary:** Orientation note for a cold-start session picking up where this one left off.
Roadmap items 1–2 are fully closed; item 3 (spec-annotation backfill) is next, and most of the
scoping work for it is already done — this file is the pointer, not a re-derivation.

**Branch context:** `main`, at commit `e95d635` (`docs(roadmap): sync Sequence for closed-out
items 1-2, prune stale [done] entries`, PR #199) or later.

## Why deferred

Item 3 is large enough (~107 specs) to warrant its own focused session rather than folding into
an already-long one. Read `docs/roadmap.md`'s `## Sequence` section first — it's the source of
truth for "what's next," not the grouped lists below it.

## Context

**Start here, in order:**
1. `docs/roadmap.md` — `## Sequence`, item 3.
2. `docs/todo/spec-annotation-backfill.md` — **already thorough**, not a stub. Covers 3 of 4
   clusters in real detail: exact file lists, exact spec-ID counts and ranges, the annotation
   convention to follow (`# @spec ID1, ID2` at the resource-block/component entry point, not per
   line — see `CP-GCP-014` in `infra/gcp/cloud_run.tf` for the existing convention), and per-file
   acceptance criteria. Don't re-derive this — read and follow it.

**Gap in that doc — the 4th cluster it doesn't cover:**
`docs/arrows/index.yaml`'s `sudoku-coach` entry has its own `drift` note, smaller and never folded
into `spec-annotation-backfill.md`:
> `SC-BE-004, SC-UI-041, SC-UI-060/061/062 verified genuinely implemented but have no @spec
> citation to extend (annotation gap only, see docs/todo/spec-annotation-backfill.md)`

That note references the backfill doc, but the doc doesn't list these 5 IDs or their files. Before
starting, either add a short "sudoku-coach" section to `spec-annotation-backfill.md` (grep each ID
in `docs/specs/sudoku-coach-specs.md` for what it covers, then find the implementing file — likely
`backend/src/main/java/com/sudoku/coach/...` for `SC-BE-004`, `ui/src/components/coach/...` for
the `SC-UI-*` ones) or explicitly scope it out and say why.

**Recommended approach:** one cluster per PR (AWS Terraform, GCP Terraform, frontend, image
recognition, and now sudoku-coach) — don't attempt all in one pass, per both the roadmap and the
backfill doc's own recommendation.

**Session-learned lesson to carry forward:** this session had a real, silent bug — a `git commit`
that dropped two files' actual content changes (only a rename and a third file's addition made it
into a squash-merged PR), caught only by diffing post-merge against `origin/main`. Root cause: the
files were never explicitly `git add`ed before commit (`git status --porcelain`'s ` M` — space
then M — means unstaged; easy to miss at a glance). **Always run `git diff --cached --stat`
immediately before every commit**, especially multi-file ones, and confirm every intended file
appears with a nonzero line count.

## What to do

1. Read `docs/roadmap.md`'s Sequence section and `docs/todo/spec-annotation-backfill.md` in full.
2. Decide how to handle the `sudoku-coach` gap (extend the backfill doc, or scope it separately).
3. Pick one cluster and work it as its own PR, following the backfill doc's existing per-cluster
   file lists and acceptance criteria.
4. Clear that cluster's `drift` note in `docs/arrows/index.yaml` once its annotations land.
5. Repeat for the remaining clusters, each as its own PR.
6. Verify every commit with `git diff --cached --stat` before committing — see the lesson above.

## Acceptance criteria

- [ ] `docs/todo/spec-annotation-backfill.md`'s existing 4 checklist items (AWS Terraform, GCP
      Terraform, frontend, image recognition) are all checked off, one PR each
- [ ] The `sudoku-coach` drift gap is either backfilled or explicitly scoped out with a stated reason
- [ ] All affected `docs/arrows/index.yaml` `drift` notes are cleared

## Related specs / docs

- [`docs/roadmap.md`](../roadmap.md) — Sequence section, item 3
- [`docs/todo/spec-annotation-backfill.md`](spec-annotation-backfill.md) — the detailed scoping
  doc for 3 of 4 clusters
- [`docs/arrows/index.yaml`](../arrows/index.yaml) — `drift` notes on `cloud-platform`,
  `react-frontend`, `image-recognition`, and `sudoku-coach`
