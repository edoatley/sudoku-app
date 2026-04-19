# Linked intent development

## Introduction

This is a look into linked  intent dev - see [here](https://github.com/jszmajda/lid?tab=readme-ov-file#2-map-the-codebase)
an attempt to make intent explicit and traceable. There's a chain of documents that translates what you want into working software:

```
HLD → LLDs → EARS → Tests → Code
```

HLD - high level design
LLD - low level design
EARS - Easy Approach to Requirements Syntax (see [this](https://loki.ws/code/2026/01/25/the-arrow-of-intent.html))


## Steps

### Step 1 - setup

```bash
/plugin marketplace add jszmajda/lid
/plugin install linked-intent-dev@jszmajda-lid
/linked-intent-dev:lid-setup
```

### Step 2 - map existing codebase

```bash
/arrow-maintenance:map-codebase
```

Token intensive process that:

1. reconnoiters the code base
2. defines potential groupings and let's you pick one - I went with domains:
    - docs/arrows/cloud-platform.md
    - docs/arrows/game-lifecycle.md
    - docs/arrows/hint-engine.md
    - docs/arrows/image-recognition.md
    - docs/arrows/index.yaml
    - docs/arrows/puzzle-generation.md
    - docs/arrows/react-frontend.md
    - docs/arrows/sudoku-logic.md
    - docs/arrows/user-management.md
3. Creates LLds from the code - you can update and improve these
4. Synthesize HLD from the LLDs
5. Define EAR linkages - Claude writes specs for current behavior, adds @spec annotations to existing code, and creates arrow docs with docs/arrows/index.yaml to tie it all together.

### Step 3 - look to take the arrows from MAPPED to AUDITED

Prompted claude to 

```claude
❯ /linked-intent-dev how should I address the tech debt and move all arrows to audited?
```

Then asked it to create a plan to address each arror one at a time each with a separate commit so I can 
thoroughly review before pushing which gave this output eventiually:

> All 7 MAPPED arrows have been moved to AUDITED across 7 commits:
> 
> │       Arrow       │ Commit  │                                Key changes                                │
> | --- | --- | --- |
> │ sudoku-logic      │ 8866c23 │ New InvalidGridException; @spec on Board/Cell; new SL-DATA-004 test       │
> │ hint-engine       │ 21a507f │ @spec on all 13 test files; NakedPair rank/label comment                  │
> │ puzzle-generation │ 6487ad9 │ @spec on 5 test files; deleted zero-assertion scratch test                │
> │ game-lifecycle    │ 5583860 │ @spec on 2 test files; fixed duplicate doc entry                          │
> │ user-management   │ a6a93ba │ @spec on 2 test files                                                     │
> │ cloud-platform    │ c581095 │ Variable source docs in variables.tf; CloudWatch alarms deferred          │
> │ react-frontend    │ b817376 │ Deleted GameControls.jsx; TutorialModal source comment; no-test gap noted │

### Step 4 - start addressing some of the issues

Will use the following issue as an example:

1. Define the change:


```claude
❯ /linked-intent-dev Can I look to ensure we are using domain objects and specific domain exceptions rather than relying on raw classes like   
List<List<List<Integer>>> and error codes. I would like an exception handling layer in the API translate exceptions to a standard error structure of code,       
message, detail  
```

After some thinking I got the following:

```claude
---
  Current State (from the arrows)

  What already exists (good):
  - InvalidGridException — domain-level, in com.sudoku.domain
  - InvalidPuzzleException — service-level, in com.sudoku.game
  - InvalidPuzzleExceptionMapper — maps InvalidPuzzleException → HTTP 422 with {"error": "..."} body
  - Board, Cell, Coordinate, CoordinateCandidate, ActionableCell — proper domain/DTO objects

  What's missing / the actual problems:

  1. Raw grid types everywhere — List<List<Integer>> (2D grid) and List<List<List<Integer>>> (candidates grid) appear in ~20 places across DTOs, service
  interfaces, GameService, SudokuService, PuzzleGenerator, Board, GameItem, etc. These should be named domain types: Grid and CandidatesGrid (or similar).
  2. Inconsistent error response structure — InvalidPuzzleExceptionMapper returns {"error": "message"}. But there's no standard error envelope (code + message +
  detail) used across all errors. Other unhandled exceptions (e.g. IllegalArgumentException, unexpected RuntimeException) would fall through to Quarkus's default
   error handling.
  3. No global exception handler — only InvalidPuzzleException has a mapper. All other exceptions are unhandled at the API boundary.

  ---
  Trade-off Sketches

  Before drafting the HLD, here are the key architectural decisions:

  Trade-off 1: Domain type for grids — typedef alias vs. wrapper class vs. record

  Option A: Type alias pattern (newtype record wrapping the list)
  public record Grid(List<List<Integer>> rows) { ... }
  public record CandidatesGrid(List<List<List<Integer>>> rows) { ... }
  Pros: Strongly typed, self-documenting, can add validation methods. Cons: Callers need .rows() to access the data; Jackson needs configuration to serialize
  transparently (or a custom serializer).

  Option B: Keep raw types in DTOs, introduce type alias only at service boundaries
  Only rename at service interfaces — DTOs stay as List<List<Integer>> since JSON serialization is cleanest with raw lists. Consequence: DTOs and domain types
  diverge; boundary conversions multiply.

  Option C: Named type aliases via typedef-style constants (Java can't do this cleanly)
  Not viable in Java.

  My recommendation: Option A — proper record wrappers with Jackson serialization configured. The serialization complexity is worth the type safety and
  self-documentation across 20+ call sites.

  ---
  Trade-off 2: Standard error envelope — where to define it

  Option A: Single shared ErrorResponse record in com.sudoku.dto
  public record ErrorResponse(String code, String message, String detail) {}
  All mappers produce this. Pros: One shape for the API consumer. Cons: detail is nullable for simple errors.

  Option B: Error envelope with a details list (for multi-field validation errors)
  public record ErrorResponse(String code, String message, List<String> details) {}
  More useful if you ever have field-level validation errors (e.g. invalid grid dimensions). Consequences: frontend must handle list.

  Option C: Keep {"error": "..."} for 422s, add new structure only for new exceptions
  Minimum change, but creates inconsistency between old and new error paths.

  My recommendation: Option A — single flat ErrorResponse(code, message, detail) matching your stated requirement. Simple, consistent, nullable detail for cases
  with no extra context.

  ---
  Trade-off 3: Global exception handler — one catch-all mapper vs. per-exception mappers

  Option A: One @Provider catch-all for Exception
  Maps unknown exceptions → 500 with a sanitized ErrorResponse. Existing specific mappers (422 for InvalidPuzzleException) take precedence by JAX-RS specificity
  rules. Pros: Nothing escapes unformatted. Cons: Might swallow errors that should be 400 or 404.

  Option B: Add targeted mappers for expected exception types + catch-all
  InvalidGridException → 400, InvalidPuzzleException → 422, GameNotFoundException (new) → 404, Exception catch-all → 500.

  Option C: Use Quarkus @ServerExceptionMapper on the Resource classes
  Per-resource handlers. More granular but duplicated across resources.

  My recommendation: Option B — a small set of targeted mappers covering known failure modes, plus a catch-all 500 mapper for surprises. Each mapper produces the
   same ErrorResponse shape.

  ---
  Please review these trade-offs and let me know your selections (or corrections). I'll then draft the full LLD update.

```

2. review any trade-offs and options for the best approaches to address them. In my case I reviewed the trade-offs and options and responded as per my needs:

```claude
❯ trade off 1 - use option A                     
  trade off 2 - use option A - perhaps have two constructors with one lacking detail and provide a helper method so the client does not risk an Null pointer     
  trade off 3 - use option B - consider the best package structure so it is easy to follow
```

3. Review LLDs

Claude then provides a full draft LLD for my review which I can provide feedback on iteratively with it 
eventually showing:

```claude
  That's the complete, finalised LLD update. Do you approve this to proceed to EARS specs?  
```

which I reply "yes" to this it generates spec updates in the docs/specs directory for review.

4. Review all the EARS specs to check they are sensible and approve. 

5. Claude creates an implementation plan

6. Claude proceeds to update the tests and code in phases