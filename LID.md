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

```
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
