# Investigation: Terraform Alternatives for `infra/aws`

**Created**: 2026-07-19
**Status**: Investigation only — no implementation. Requested in [#139](https://github.com/edoatley/sudoku-app/issues/139).
**Scope**: Evaluate CloudFormation, AWS CDK, AWS SAM, CDK for Terraform (CDKTF), OpenTofu, Pulumi, and raw AWS SDK/CLI scripting as alternatives to the existing Terraform setup in `infra/aws/`. No code changes.

## Summary / Recommendation

**Stay on Terraform.** None of the alternatives solve a problem this project actually has, and every one of them costs a full state-migration plus a rewrite of a mature, working, security-reviewed 1,700-line configuration. If anything changes this conclusion, it's HashiCorp's license — see [OpenTofu](#5-opentofu) below, which is the one alternative worth revisiting periodically rather than dismissing outright.

| Option | Verdict |
| --- | --- |
| Stay on Terraform (HashiCorp) | **Recommended** — no forcing function to move |
| OpenTofu | Worth watching; near-zero-cost switch if HashiCorp licensing/pricing becomes a problem |
| AWS CDK | Not recommended — trades a mature setup for a less mature one, for a benefit (real code) this project doesn't need |
| AWS SAM | Not recommended — Lambda/API-Gateway-only, this stack is bigger than that (Cognito, Amplify, Route53, Budgets, IAM) |
| CDKTF | Not recommended — same Terraform engine/state, only the authoring language changes; adds complexity for no operational gain |
| CloudFormation (raw) | Not recommended — no cross-account remote-state reads, verbose JSON/YAML, no workspaces |
| Pulumi | Not recommended — real license risk for a project this size (org/team pricing beyond the free tier), and no capability gap it closes |
| Raw AWS SDK / CLI scripting | Not recommended — reinvents drift detection, state, and dependency ordering that Terraform already gives for free |

## Current State (what would have to move)

`infra/aws/` is 16 files, ~1,700 lines of HCL, provisioning:

- **Compute**: 2 Lambdas (Java/Quarkus with SnapStart, Python container image for image recognition)
- **API**: API Gateway v2 (HTTP API) with a JWT authorizer, `$default` catch-all route, per-route throttling
- **Data**: 4 DynamoDB tables (`PAY_PER_REQUEST`, selective PITR)
- **Auth**: 2 Cognito User Pools (prod + shared RC), Google OAuth IdP, 2 app clients per pool
- **Frontend hosting**: Amplify app/branch with inline build spec, `VITE_*` env injection
- **DNS/TLS**: 2 Route53 zones, Amplify-managed ACM certs, manual NS delegation to a separate AWS account
- **IAM**: 2 execution roles, 6+ scoped inline policies
- **Cost controls**: AWS Budgets with an automatic IAM-deny action, Cost Anomaly Detection

Non-trivial characteristics that any replacement has to reproduce, not just the resources:

1. **Workspace-based multi-environment isolation** (`default` / `rc-shared` / `rc-*`), with selective resource sharing (shared Cognito pool, shared S3 zip bucket) and `local.suffix` naming — see `docs/llds/cloud-platform.md`.
2. **Cross-workspace remote-state reads** (`terraform_remote_state` for the beta Route53 zone ID) — this is a first-class Terraform/OpenTofu feature; CloudFormation has no equivalent, CDK/Pulumi require bespoke SSM Parameter Store or stack-output plumbing to replicate it.
3. **`moved{}` block migration history** — the project has already executed one large refactor (bespoke resources → `cloudposse`/`terraform-aws-modules` registry modules) with zero resource destruction, using native state-surgery primitives (`docs/planning/old/infra-migration.md`, `docs/planning/old/infra-review.md` M7). A tool switch means re-doing that exercise in whatever the new tool's import/adopt mechanism is, for every one of ~15 resource types, across 3+ live workspaces (`default`, `rc-shared`, plus any active `rc-*`), with zero acceptable downtime margin (production DNS, Cognito pools, and DynamoDB tables with user data).
4. **Two-phase apply** to avoid a 40-minute ACM wait blocking every deploy (`ci-deploy.yml`), driven by `terraform plan -target`-style phase separation.
5. **CI integration**: GitHub Actions OIDC role assumption, `hashicorp/setup-terraform`, S3 native-lock backend, `terraform fmt`/`validate` gates, and multiple scripts (`terraform-plan.sh`, `deploy-local.sh`, `destroy-rc.sh`) that shell out to the `terraform` CLI directly.
6. A recent, thorough security/cost review (`docs/planning/old/infra-review.md`) already found and fixed 15 findings across this exact codebase (deletion protection, IAM scoping, CORS, budget coverage). That review's conclusions are HCL-specific line references; a rewrite in another tool invalidates the audit trail and requires re-review.

Migrating anything here means re-earning 1 and 2 in whatever the new tool's idiom is (they're not automatic), and re-running an equivalent of the security review, before the switch could be trusted with production.

## Evaluation Criteria (for this project specifically)

This is a solo/small-team personal project, not an enterprise platform team. The criteria that matter here are different from a "which IaC tool should Big Co standardize on" comparison:

- **Cost**: must stay free or near-free at this scale (no org/team-tier licensing).
- **Effort to migrate**: has to preserve existing resources (zero data loss on DynamoDB/Cognito, zero DNS downtime) via some import/adopt mechanism.
- **Multi-environment story**: must support the existing workspace-per-branch pattern without a full redesign.
- **AWS-only scope**: no need for multi-cloud portability — a stated non-goal.
- **Solo-maintainer readability**: the next person reading this (including future-you) shouldn't need a new mental model on top of already-learned Terraform/HCL, unless the payoff is large.
- **CI/CD fit**: must work cleanly in GitHub Actions with OIDC, matching the existing two-phase-apply / smoke-test pipeline.

## Alternatives Considered

### 1. AWS CloudFormation (native, JSON/YAML)

**What it is**: AWS-native, no third-party state backend — AWS holds state for you.

**Pros**:
- No state file to manage or lock (AWS-hosted); removes the S3 backend + native-lock dependency entirely.
- Zero extra IAM trust — CloudFormation execution roles are simpler to scope than a Terraform CI OIDC role, since AWS validates template permissions natively.
- StackSets could theoretically replace the workspace-per-branch pattern.

**Cons**:
- **No cross-account/cross-stack remote-state read equivalent** to `terraform_remote_state` — the RC-workspace-reads-default-workspace's-zone-ID pattern would need to become SSM Parameter Store lookups or nested-stack exports, both clunkier and workspace-unaware.
- No community-module ecosystem equivalent to the `cloudposse`/`terraform-aws-modules` registry this project already depends on; the recent bespoke→module refactor would be undone.
- JSON/YAML has no locals, no ternaries beyond `!If`, no loops beyond `Fn::ForEach` (recent, limited) — the extensive conditional logic here (`is_default`, `is_rc`, `local.suffix`-based naming across every resource) would balloon in verbosity.
- No `terraform plan`-equivalent diff quality; change sets are harder to read in CI logs.
- AWS-only by construction — not a concern for this project, but also not a benefit over Terraform's AWS provider, which is equally AWS-only in practice here.

**Verdict**: Worse on every axis that matters here except state-hosting. Not recommended.

### 2. AWS CDK (TypeScript/Python/Java, synthesizes to CloudFormation)

**What it is**: Write actual imperative code (constructs), which synthesizes to a CloudFormation template under the hood.

**Pros**:
- Real programming language — loops, functions, and abstraction are natural instead of HCL workarounds. The `local.suffix`-based multi-environment naming, repeated across every `.tf` file, would become a single reusable construct/function.
- L2/L3 constructs (e.g., `aws-lambda`, `aws-apigatewayv2`) encode AWS best practices with sensible defaults, similar in spirit to the `terraform-aws-modules` registry modules already adopted.
- Strong typing catches some classes of error at compile time that Terraform only catches at `plan`/`apply` time.

**Cons**:
- Still synthesizes to CloudFormation, so it inherits CloudFormation's cross-stack/remote-state weaknesses above — the workspace-remote-state pattern still needs reworking.
- No adoption/import story as smooth as Terraform's `import{}` block + `moved{}` block combination that this project already used successfully (`infra-review.md` M3, M5, M7) — CDK's `cdk import` exists but is less mature and wasn't built for a resource set this heterogeneous (Cognito + Amplify + Route53 + budget actions).
- New language/tooling to maintain (Node.js or Python CDK toolchain) alongside the existing Java/Quarkus + Node/Vite stack — another runtime in CI.
- Team of one: the HCL learning investment is sunk. CDK's benefit (avoiding HCL's limitations) is real but marginal at ~1,700 lines; it becomes compelling mainly past several thousand lines or with multiple contributors who already know the target language.
- CDK "bootstrap" stacks and asset buckets add AWS account setup similar to (not less than) Terraform's S3 backend bootstrap already in place (`scripts/infra/bootstrap.sh`).

**Verdict**: Genuinely the strongest alternative on developer ergonomics, but the migration cost (redo state adoption, redo the security review, learn a new toolchain) isn't justified by a benefit this project isn't currently feeling pain from. Revisit only if the HCL conditional logic becomes unmanageable, which it isn't yet (16 files stay well-organized per `docs/llds/cloud-platform.md`'s File Organisation table).

### 3. AWS SAM (Serverless Application Model)

**What it is**: A CloudFormation extension + CLI, purpose-built for Lambda-centric serverless apps with a simplified template syntax and a good local-invoke/debug experience (`sam local`).

**Pros**:
- Excellent local Lambda development loop (`sam local invoke`, `sam local start-api`) — a genuine ergonomic win over what exists today.
- Simpler syntax than raw CloudFormation for the Lambda + API Gateway subset.

**Cons**:
- SAM's simplified syntax only covers Lambda/API Gateway/DynamoDB/EventBridge-shaped resources well. This stack also has Cognito, Amplify, Route53, ACM, IAM budget actions, and Cost Anomaly Detection — all of which fall back to raw CloudFormation resources inside a SAM template anyway, at which point SAM's syntax advantage mostly disappears and you're back to CloudFormation's cons above.
- Same remote-state/cross-stack limitations as CloudFormation.
- SAM is explicitly scoped to "serverless applications" — this project is serverless compute plus a non-trivial amount of platform plumbing (DNS delegation across accounts, Cognito social IdP, Amplify hosting) that's outside SAM's sweet spot.

**Verdict**: The local-Lambda-debug story is nice but doesn't offset having to hand-roll everything else in raw CloudFormation underneath. Not recommended as a full replacement; `sam local` could theoretically be adopted standalone for local Lambda testing without touching the Terraform-managed infra at all, but that's a separate, smaller idea outside this issue's scope.

### 4. CDK for Terraform (CDKTF)

**What it is**: Write CDK-style imperative code (TypeScript/Python), but synthesize to Terraform HCL/JSON and execute with the actual Terraform engine and state backend.

**Pros**:
- Keeps every operational property this project already relies on: the S3 native-lock backend, the `cloudposse`/`terraform-aws-modules` registry modules (CDKTF can wrap existing Terraform modules directly), `terraform plan`/`apply` semantics, and the existing `import`/`moved` migration tooling all still apply unchanged.
- Adds real-language ergonomics (loops/functions for the `local.suffix` pattern) without changing the runtime engine.

**Cons**:
- Adds a synthesis step (CDKTF → generated `.tf.json`) and a new language toolchain, for a benefit that's purely ergonomic — the underlying HCL/state model doesn't change, so none of the "why switch" pain points (remote state, workspaces, modules) are actually about CDKTF's problem space.
- Generated `.tf.json` is harder to read/diff in PR review than the current hand-written HCL — a regression for a solo maintainer who currently reviews plan output and `.tf` diffs directly.
- Smaller community/ecosystem than plain Terraform; less Stack Overflow/AI-training-data coverage for troubleshooting than either HCL or CDK proper.

**Verdict**: Interesting middle ground in the abstract, but for this project it adds a build step and reduces diff-review clarity without solving a problem that exists today. Not recommended.

### 5. OpenTofu

**What it is**: A Linux-Foundation-governed, MPL-licensed fork of Terraform, created in response to HashiCorp's 2023 relicensing of Terraform from MPL 2.0 to the Business Source License (BUSL). Drop-in compatible with existing HCL and state files as of the fork point, with its own independent feature development since.

**Pros**:
- **Near-zero migration cost**: same HCL, same state file format, same provider ecosystem (including the exact `cloudposse`/`terraform-aws-modules` modules already in use), same `import{}`/`moved{}` blocks. In practice: change the binary CI installs and the `required_version` constraint; everything else in `infra/aws/*.tf` is unchanged.
- Removes any future licensing risk from HashiCorp's BUSL terms (which restrict *competing* commercial products, not personal/internal use — not currently a problem for this project, but a live risk category Terraform-proper carries and OpenTofu doesn't).
- Native S3-locking (`use_lockfile`) and other post-fork features have been kept at parity or ahead in some areas.

**Cons**:
- Slightly smaller ecosystem/momentum than HashiCorp Terraform, though the gap has been closing since 2023 and the provider registry (what actually matters day-to-day) is shared/compatible.
- No functional gap being closed today — this project's BUSL exposure is already effectively nil (personal project, not a competing IaC-as-a-service product), so switching now is prevention, not cure.

**Verdict**: The one alternative that's genuinely low-cost to adopt, because it isn't really a different tool — it's the same tool under different governance. Not urgent, but worth a one-line follow-up ticket to swap `hashicorp/setup-terraform` for `opentofu/setup-opentofu` in CI if HashiCorp's licensing terms tighten further, or simply on a routine maintenance pass. No urgency today.

### 6. Pulumi

**What it is**: IaC in general-purpose languages (TypeScript, Python, Go, etc.), backed by its own state/engine (not CloudFormation, not Terraform) — closest in spirit to "CDK but multi-cloud and not CloudFormation-based."

**Pros**:
- Real language ergonomics like CDK, without CloudFormation's cross-stack limitations — Pulumi has genuine cross-stack references (`StackReference`) that work more like Terraform's `terraform_remote_state`.
- Can consume the Terraform provider ecosystem directly (Pulumi's AWS provider is generated from the Terraform AWS provider), so resource coverage parity is good.
- Self-managed state backend option (including S3) avoids forced use of Pulumi's hosted service.

**Cons**:
- **Licensing/cost risk**: Pulumi's free tier is generous for individuals, but the product is oriented around Pulumi Cloud's paid tiers for teams; even using a self-managed S3 backend to avoid that, the tool's development and support incentives point toward the hosted product. Worth flagging explicitly since the issue asks about pros/cons of moving — this is the clearest "why not" of any option evaluated here for a project trying to stay free.
- No import/adopt story with a track record on *this* codebase the way Terraform's `import{}`/`moved{}` blocks already have (see Current State point 3) — every one of the ~15 resource types would need to be re-imported into Pulumi state from scratch, another from-zero adoption exercise.
- Smallest community footprint of any option here for AWS-specific serverless patterns (Lambda/API Gateway v2/Cognito/Amplify) compared to Terraform's registry-module ecosystem.
- New language toolchain in CI, same as CDK/CDKTF, for a project that already juggles Java/Quarkus + Node/Vite + Python (image recognition).

**Verdict**: Capable tool, but combines CDK's migration cost with a licensing model less clearly free-forever than Terraform/OpenTofu/CloudFormation for a project explicitly optimizing for near-zero cost. Not recommended.

### 7. Raw AWS SDK / CLI scripting (no declarative IaC)

**What it is**: Drop declarative IaC entirely; provision everything via `aws` CLI commands or a boto3/AWS SDK script, as `scripts/bootstrap.sh` already does for the handful of resources deliberately kept outside Terraform (ECR repo, S3 state bucket, GitHub OIDC provider — see `infra/aws/README.md` Bootstrap section).

**Pros**:
- No new tool to learn — plain `aws` CLI/SDK calls, which the project already uses for the bootstrap script and several operational scripts (`add-admin.sh`, `test-budget-deny.sh`, `amplify-remove-rc-urls.sh`).
- Total control, no abstraction leakage.

**Cons**:
- Loses declarative drift detection, `plan` previews, and dependency-graph ordering entirely — every one of these would need to be hand-rolled (checking current state before mutating, computing what changed, ordering ~15 interdependent resource types correctly) for every deploy. Terraform's core value proposition *is* this, not resource CRUD, which the SDK already does fine.
- The project's own bootstrap-script comment/pattern is a live example of why this doesn't scale: those resources are kept minimal and rarely-changing (created once, idempotent re-run) specifically *because* they're unmanaged by Terraform. Everything else was deliberately put under Terraform once it needed ongoing, safe, reviewable changes.
- No audit trail equivalent to `terraform plan` output in CI logs for reviewing what a deploy will change before it happens — a regression from the current PR-reviewable plan output.

**Verdict**: Fine for the narrow bootstrap case it's already used for; would be a significant regression as a replacement for the other 1,700 lines of ongoing, actively-changed infrastructure. Not recommended.

## Decision

No change recommended. Terraform is working, is well-documented (`docs/llds/cloud-platform.md`), was recently security-reviewed end-to-end (`docs/planning/old/infra-review.md`), and none of the alternatives solve a problem this project currently has. The evaluation surfaced one low-cost, no-regrets follow-up worth tracking separately if it ever becomes relevant: switching the CI Terraform binary from HashiCorp's to OpenTofu's, which requires no HCL changes given the current 100%-compatible fork status.

## References

- `infra/aws/*.tf` (16 files, ~1,700 lines) — current implementation
- `infra/aws/README.md` — operational documentation
- `docs/llds/cloud-platform.md` — architecture LLD for this component
- `docs/planning/old/infra-review.md` — prior security/cost review of this exact codebase
- `docs/planning/old/infra-migration.md` — record of the bespoke-to-module refactor already completed
