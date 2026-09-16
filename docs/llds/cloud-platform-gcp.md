# Cloud Platform — GCP Facet (Pulumi)

**Created**: 2026-09-11
**Status**: In Progress — design approved, implementation phased
(`docs/planning/gcp-pulumi-replatform.md`)

## Context and Current State

The GCP facet provisions the same runtime platform as the AWS facet on GCP-native services, in
region `us-central1` (chosen for free-tier coverage, accepting a residency shift under the
one-cloud-at-a-time premise).

**This document describes the Pulumi design, which is the current intent.** The facet is being
re-platformed from Terraform in a clean-room GCP project; the outgoing `infra/gcp/*.tf` and the
six `scripts/infra/gcp/*.sh` bootstrap scripts remain live against the *old* project until the
production cutover, and are deleted immediately after. Where this document says "is", it means
"is, in the Pulumi target" — the migration phases and their ordering are in the plan.

Two properties distinguish this facet from AWS and are load-bearing:

- **There is no gateway.** Cloud Run is exposed directly, so in-app JWT validation and in-app
  CORS are the *sole* gate. A missing `@Authenticated` is an open endpoint on GCP, which is why
  the route-coverage test asserting every non-public route rejects anonymous requests is a
  compensating control, not a nicety.
- **There are no long-lived credentials.** CI federates via Workload Identity Federation;
  runtime services authenticate to Firestore and Vertex AI with their own service accounts via
  ADC. After de-Bedrocking, Secret Manager is removed from the project entirely.

Files: `infra/gcp/pyproject.toml`, `infra/gcp/uv.lock`, `infra/gcp/components/*.py`,
`infra/gcp/bootstrap/`, `infra/gcp/app/`, `infra/gcp/tests/`.

## The Two-Stack Model

The HLD's original GCP tenet preferred *manual* identity provisioning — service accounts, IAM,
WIF and Identity Platform were created by reviewed bash rather than IaC, on the reasoning that
the identity layer should be a deliberate learning surface kept out of automation. That tenet is
**deliberately revised**. It produced six bash scripts that were the least-tested and most
drift-prone part of the system, while the privilege concern it protected is better served by
splitting the deploying identity in two.

| | `sudoku-gcp-bootstrap` | `sudoku-gcp-app` |
| --- | --- | --- |
| Run by | a human, locally, `gcloud auth application-default login` | GitHub Actions, as the WIF-federated deploy SA |
| Frequency | ~never after initial setup | every deploy |
| Owns | project, billing link, API enablement, GCS state bucket, KMS keyring + key, Artifact Registry, all three service accounts, all project IAM, WIF pool + provider, the billing budget | Firestore, Cloud Run ×2, Firebase project + Hosting site + custom domain, Cloud DNS zone + records, Identity Platform config + Google IdP |
| Stacks | `prod` only | `prod` + N ephemeral `rcg-*` |
| Secrets provider | passphrase | Cloud KMS (`gcp-kms://…`) |

**The CI identity cannot grant itself privilege.** It holds no IAM-admin role, no WIF-admin role,
and no billing-account role. Net change against the previous manual model: **+1 role**
(`roles/identityplatform.admin`), **−all Secret Manager roles**, **no billing reach**.

**Why the budget lives in `bootstrap`, not `app`.** `gcp.billing.Budget` is scoped to the
*billing account*, not the project. Granting a repo-federated CI identity `billing.budgets.*`
would give it write access across every project on that account, including unrelated ones — a
materially worse blast radius than the Terraform model it replaces. The budget changes about once
a year; it belongs with the human-run stack. Its Pub/Sub topic and notification channel go with
it, being the budget's own plumbing rather than application resources.

**Why `bootstrap` uses a passphrase and not KMS.** It is creating the KMS key, so it cannot
encrypt itself with it. It holds no secrets — the only secret in the system is the Google OAuth
client secret, which belongs to the `app` stack — so a passphrase is honest rather than a
weakness.

## Project Layout

```
infra/gcp/
├── pyproject.toml            # uv project; installable, exporting `components`
├── uv.lock                   # COMMITTED — providers are pinned, unlike the .tf lock files
├── components/               # shared ComponentResource library, imported by both projects
├── bootstrap/{Pulumi.yaml,Pulumi.prod.yaml,__main__.py}
├── app/{Pulumi.yaml,Pulumi.prod.yaml,__main__.py}
└── tests/                    # pulumi.runtime.set_mocks unit tests
```

Each `Pulumi.yaml` declares:

```yaml
runtime:
  name: python
  options:
    toolchain: uv
    virtualenv: ../.venv
```

so `pulumi up` resolves dependencies from `pyproject.toml` via `uv` with no shell wrapper.

**One provider, not two.** `pulumi-gcp` is generated from the merged GA + `google-beta` upstream
providers and ships both in a single SDK, so the `provider = google-beta` lines that
`firebase_hosting.tf` needs simply vanish. `gcp.firebase.*` and `gcp.identityplatform.*` are
available from the same import.

## Component Library

Every component subclasses `pulumi.ComponentResource`, parents its children with
`opts=ResourceOptions(parent=self)`, and registers typed outputs. Token namespace
`sudoku:gcp:<Name>`.

| Component | Stack | Wraps | Key outputs |
| --- | --- | --- | --- |
| `ProjectFoundation` | bootstrap | `organizations.Project` (`auto_create_network=False`), `billing.ProjectInfo`, `projects.Service` ×N, `storage.Bucket`, `kms.KeyRing`, `kms.CryptoKey` | `project_id`, `project_number`, `state_bucket_url`, `kms_key_uri` |
| `ArtifactRegistry` | bootstrap | `artifactregistry.Repository` + cleanup policies | `repository_url` |
| `ServiceIdentity` | bootstrap | `serviceaccount.Account`, `projects.IAMMember` ×N | `email`, `member` |
| `WorkloadIdentityFederation` | bootstrap | `iam.WorkloadIdentityPool`, `iam.WorkloadIdentityPoolProvider`, `serviceaccount.IAMMember` | `provider_resource_name`, `principal_set` |
| `CostGuardrails` | bootstrap | `pubsub.Topic`, `monitoring.NotificationChannel`, `billing.Budget` | `budget_name`, `topic_id` |
| `FirestoreDatabase` | app | `firestore.Database`, `firestore.Field` (TTL), `firestore.Index` ×N | `name` |
| `ContainerService` | app | `cloudrunv2.Service`, `cloudrunv2.ServiceIamMember` | `url`, `service_name`, `latest_ready_revision` |
| `IdentityPlatform` | app | `identityplatform.Config`, `identityplatform.DefaultSupportedIdpConfig` | `issuer`, `audience` |
| `StaticSite` | app | `firebase.Project`, `firebase.HostingSite`, `firebase.HostingCustomDomain` | `site_id`, `default_url`, `custom_domain_url` |
| `DnsZone` | app | `dns.ManagedZone`, `dns.RecordSet` ×N | `name_servers` |

### Removing a binding

The additive model can express "this grant exists", never "this grant must not". Removing a
Google-created default is therefore an imperative, one-off act — there is no `IAMMember` that
means absent, and reaching for `IAMPolicy` to get one would delete the operator's Owner grant and
every service agent along with it.

Done once, 2026-09-13: `roles/editor` on the **default compute service account**
(`<project-number>-compute@developer.gserviceaccount.com`). Google creates that binding; nothing
here uses the account — Cloud Run always runs as `sudoku-run`, asserted by a test — so it was
standing broad privilege on an unused identity. Removed with
`gcloud projects remove-iam-policy-binding`.

Because the removal is not codified, nothing stops it returning. `scripts/infra/gcp/inventory.sh`
is what notices: it classifies that account separately from the per-API service agents precisely
so a reappearance stands out rather than blending into expected noise.

### IAM: additive only

**Use `gcp.projects.IAMMember`. Never `IAMBinding` (authoritative for a role) and never
`IAMPolicy` (authoritative for the whole project).** An `IAMPolicy` would silently delete every
other binding on the project, including the operator's own Owner grant. This is the single
largest footgun in the migration, and it is guarded structurally: `tests/test_no_authoritative_iam.py`
scans the component sources and fails the build if either appears. @spec CP-PUL-013

### `ContainerService` — the flagship

Instantiated twice, for the backend and image-recognition services, from one class. It replaces
`cloud_run.tf` (158 lines) and `image_recognition.tf` (129 lines), which are roughly 70%
identical.

It grants the `allUsers` invoker on **every** stack including `prod`. The Terraform facet kept
prod's invoker manual (`scripts/infra/gcp/grant-prod-invoker.sh`, gap G1) so that a plan could not
toggle production's reachability. Under the two-stack split that argument no longer holds: the
app stack already has `roles/run.admin` and can delete the service outright, so withholding one
additive binding buys nothing and costs a mandatory manual step on every service recreation. The
binding grants **network reachability only** — each service still validates the caller's JWT
in-app. @spec CP-PUL-023

### Dynamic outputs and stack references

Three places exercise values that do not exist until apply time, and they are the clearest
argument for a general-purpose language over HCL:

**The secrets-provider URI** is assembled from a resource attribute and consumed by the *next*
stack's `pulumi stack init --secrets-provider`:

```python
self.kms_key_uri = pulumi.Output.concat("gcp-kms://", key.id)
```

**The WIF principal set** must be built from the pool's server-assigned resource name — which is
why `github-bootstrap.sh` has to shell out to `gcloud ... --format='value(name)'`:

```python
principal_set = pulumi.Output.concat(
    "principalSet://iam.googleapis.com/", pool.name,
    "/attribute.repository/", github_repo,
)
```

**The container image URI** is assembled from a *different stack's* resource attribute and a
per-run config value, so CI never hand-builds a registry path that a repository rename could
silently desync:

```python
image = pulumi.Output.concat(
    bootstrap.require_output("artifact_registry_url"), "/backend:", config.get("backendImageTag")
)
```

The frontend origin the backend must allow through CORS is the contrasting case, and the contrast
is the point. On AWS the equivalent value — the Amplify URL — is genuinely unknown until apply,
and closing the loop needs a post-apply `aws cognito-idp update-user-pool-client` call, with the
dependency living outside state. On GCP the Hosting origin is `https://{site_id}.web.app`, and
`site_id` is derived by `naming.py` from the stack name, so the whole list is computable before
anything is created:

```python
cors = naming.cors_allowed_origins(stack, project_id, custom_domain)
backend = ContainerService(..., env=backend_env(..., cors_origins=cors))
```

No `Output` is needed, no post-apply step exists, and CI derives the identical string from the
identical function. The circular dependency does not resolve more elegantly here — it never
forms.

The `app` stack reads `bootstrap`'s outputs by `StackReference`:

```python
bootstrap      = pulumi.StackReference("organization/sudoku-gcp-bootstrap/prod")
project_id     = bootstrap.require_output("project_id")
run_sa         = bootstrap.require_output("run_service_account_email")
ar_repo_url    = bootstrap.require_output("artifact_registry_url")
backend_image  = pulumi.Output.concat(ar_repo_url, "/backend:", config.require("backendImageTag"))
```

Every ephemeral stack references the same bootstrap stack.

## Stack Strategy

Pulumi stacks replace Terraform workspaces one-for-one, with one rename: **`default` → `prod`**.
`default` was a Terraform artefact; Pulumi has no such concept and `prod` is unambiguous.

| Stack | Purpose |
| --- | --- |
| `prod` | Production. Firestore `(default)` database, no name suffix, PITR + delete protection, custom domain attached. |
| `rcg-<branch>` | Ephemeral per-branch environment. Named `sudoku-<stack>` Firestore database, own Hosting site, `-<stack>` suffix throughout. |

Stack-name derivation lives in `components/naming.py` and is shared by CI and the Pulumi program,
so the two cannot drift: lowercase, `/.` → `-`, strip to `[a-z0-9-]`, cap at
`30 - len(project_id) - 1`, strip any trailing hyphen. The 30-character cap is the Firebase
Hosting `site_id` limit — a Firebase constraint, not a Terraform one, so it survives the move.
`naming.py` is the sole implementation; CI shells into it rather than reimplementing it in bash.

Per-run values are passed non-persistently rather than committed:

```bash
pulumi up --yes --stack "$STACK" \
  -c sudoku:backendImageTag="$BACKEND_TAG" \
  -c sudoku:imageRecognitionImageTag="$IR_TAG"
```

Ephemeral lifecycle — note that `--force` is **prohibited**, because `pulumi stack rm --force`
deletes the stack while orphaning its live resources:

```bash
pulumi stack select --create "$STACK" --secrets-provider="$PULUMI_KMS_KEY"
pulumi up --yes
# on branch delete:
pulumi destroy --yes && pulumi stack rm --yes
```

## Resources

### Compute (Cloud Run)

Both services run with `min_instance_count = 0`. GCP has no request-rate throttle equivalent to
API Gateway's; load and spend are bounded by `max_instance_count` × container concurrency, which
is the documented, accepted substitute. Each service runs as its own runtime service account,
never the default compute SA. @spec CP-GCP-001, CP-GCP-002, CP-GCP-003, CP-GCP-004, CP-GCP-013

The two services carry deliberately different environments. Backend:
`QUARKUS_PROFILE=gcp`, `QUARKUS_CONFIG_PROFILE_PARENT=prod`, `GCP_PROJECT_ID`,
`QUARKUS_GOOGLE_CLOUD_PROJECT_ID`, `CORS_ALLOWED_ORIGINS`, `COACH_AI_PROVIDER=vertex`,
`GCP_REGION`. Image recognition: `GCP_PROJECT_ID`, `CORS_ALLOWED_ORIGINS`,
`IMAGE_AI_PROVIDER=vertex`. **No AWS environment variables on either service.**

`GCP_REGION` is backend-only, and that asymmetry is load-bearing. Both services read it as a
Vertex endpoint location, but they need different answers: the coach's `gemini-2.5-flash-lite` is
regional and tracks the Cloud Run region, while image recognition's `gemini-3.8-flash` is served
**only** from the `global` endpoint, which is what `providers/vertex.py` falls back to when the
variable is absent. Setting it on image recognition returns a 404 whose message suggests the model
does not exist or is not permitted — it reads as a naming or entitlement fault rather than a
location one, so a unit test asserts the variable's absence. The model itself stays unpinned in
infrastructure: `VERTEX_MODELS` is an override, and the default in `providers/vertex.py` is the
value the accuracy gate cleared.

### Persistence (Firestore)

Native mode, `us-central1`. Collections `games`, `players`, `leaderboard`, `coachRateLimits`. A
TTL policy on `coachRateLimits.expiresAt`, and a composite index on
`games(userId ASC, status ASC, endedAt DESC)`. PITR and delete protection on `prod` only, plus
`ResourceOptions(protect=True, retain_on_delete=True)` — strictly stronger than Terraform's
`deletion_policy=ABANDON`, because `protect` also blocks replacement.
@spec CP-GCP-020, CP-GCP-021, CP-GCP-022, CP-GCP-023, CP-GCP-024

The clean-room rebuild starts with an **empty** Firestore. Existing users are not stranded — see
*Cross-Cloud Identity Continuity* in `docs/llds/cloud-platform.md` — but the GCP leaderboard and
game history reset at cutover.

### Identity (Identity Platform)

Provisioned **as code**, reversing `CP-GCP-031`. `identityplatform.Config` carries
`authorized_domains` (itself an `Output.all(...)` over `localhost`, `<project>.firebaseapp.com`,
`<project>.web.app` and the custom domain) and the email/password sign-in used only by the CI
smoke user; `DefaultSupportedIdpConfig` carries the Google IdP with its client id and
KMS-encrypted secret. @spec CP-PUL-030

Two things stay manual and cannot be otherwise:

- **The OAuth consent screen and the OAuth 2.0 client.** There is no GCP API to create OAuth
  client IDs. This is irreducible on both clouds.
- **The Identity Platform smoke-test *user*.** No IaC resource exists for it;
  `scripts/infra/gcp/create-smoke-user.sh` is retained. @spec CP-GCP-032

The new project needs its **own** OAuth client — the existing one lives in the old project and is
shared with AWS Cognito's Google federation, so deleting the old project would break AWS. A
second client for Cognito, and repointing `infra/aws/cognito.tf` at it, is a prerequisite for
that deletion.

### Frontend Hosting and DNS

Firebase Hosting with an SPA rewrite of all paths to `/index.html`, deployed by CI after
`pulumi up` so build-time `VITE_*` values reflect the applied infrastructure. Each stack gets its
own Hosting site, and CI targets it by `site_id` — closing the gap where RC frontends had a site
resource but no deploy target. @spec CP-GCP-040, CP-GCP-041, CP-GCP-042, CP-GCP-043

DNS reverses `CP-GCP-050`. Pulumi owns a Cloud DNS managed zone for `gcp.edoatley.co.uk`,
delegated once by an NS record added by hand in the Route53 `edoatley.co.uk` zone; thereafter
every GCP record is Pulumi-managed and AWS is never touched. The production host becomes
`sudoku.gcp.edoatley.co.uk`, with Google-managed TLS and no manual certificate provisioning.
@spec CP-PUL-050, CP-GCP-051

`CP-GCP-050`'s original rationale — *"Cloud DNS has no apex-alias equivalent, so the AWS-style
delegated-subzone approach doesn't apply"* — only bites at a **zone apex**. The new host sits
inside a delegated zone, so an ordinary CNAME record works and the objection does not apply.

### AI Inference

Vertex AI for both the coach (`gemini-2.5-flash-lite`, `VertexCoachClient`) and image recognition
(`gemini-3.8-flash`, a full model rather than the coach's `flash-lite` because grid OCR is
materially harder than text generation — `gemini-2.5-flash` scored 92.6% against the fixture set
where 3.8-flash scores 100%), authenticated by each runtime service account via ADC. **No cross-cloud Bedrock, no AWS access
key, no Secret Manager.** @spec CP-GCP-090, CP-GCP-089

### Cost Guardrail

A monthly Cloud Billing budget publishing threshold alerts to a Pub/Sub topic at 80% actual and
100% forecast. GCP budgets cannot attach a deny action the way AWS Budgets can, so automated
enforcement remains deferred (`CP-GCP-061`). @spec CP-GCP-060

The Cloud DNS managed zone (~$0.20/month) is the first standing charge on the GCP side; the
free-tier tenet is otherwise intact — Cloud Run scales to zero, Firestore has a free daily quota,
and Firebase Hosting has a free tier.

### Labels

`project=sudoku`, `managed_by=pulumi`, and `environment` (`prod` on the prod stack, else the
sanitised stack name), applied as provider default labels. Values are lowercase `[a-z0-9_-]`,
≤63 chars. @spec CP-GCP-070

## Manual Setup

**There is no bash prelude.** The `bootstrap` stack creates the project, links billing, enables
the APIs, and creates the state bucket and KMS key — everything the old `bootstrap.sh` and
`github-bootstrap.sh` did. It is run from a developer machine, and it is the only thing that is.

What remains manual is **inputs and one cross-cloud record**, not resources:

| Step | Why it cannot be code |
| --- | --- |
| `gcloud auth application-default login` as a principal able to create projects and attach the billing account | Authenticating is an action, not a resource. Pulumi has to run *as* somebody. |
| Supply the billing account id as stack config (`gcp:billingAccount`) | An input value. The account already exists and is not managed here. |
| OAuth consent screen + OAuth 2.0 client | **No GCP API creates OAuth client IDs.** Irreducible on both clouds. (`CP-PUL-032`, deferred permanently.) |
| One NS record delegating `gcp.edoatley.co.uk` in the Route53 parent zone | The parent zone lives in another cloud and is deliberately out of scope. One record, once. |
| The Identity Platform smoke-test user | No IaC resource exists for it. (`CP-PUL-031`, deferred.) |

Everything else — APIs, service accounts, IAM, WIF, Artifact Registry, Identity Platform config,
the prod invoker, DNS records — is code. Six bash scripts are deleted.

### Bootstrapping Pulumi's own state

Pulumi cannot store state in a bucket that does not exist yet. Rather than create that bucket by
hand, `bootstrap`'s **first** run uses the local filesystem backend and then migrates into the
bucket it just created, which keeps "100% Pulumi-built" literally true.

The same ordering applies to its secrets: `bootstrap` is created under a passphrase because it is
creating the KMS key, then re-keyed onto that key once it exists. Leaving it on the passphrase
does not stay a local concern — the `app` stack reads `bootstrap` by `StackReference`, which
constructs that stack's secrets manager, so every CI run would need the passphrase and the
project would have acquired exactly the kind of long-lived shared credential it exists to avoid.
Both stacks therefore end on KMS, which the deploy service account can already use.

```bash
cd infra/gcp/bootstrap
pulumi login --local
pulumi stack init prod --secrets-provider=passphrase
pulumi up                    # project, billing link, APIs, state bucket, KMS, SAs, WIF, budget

pulumi stack export --file bootstrap-state-backup.json
pulumi login gs://sudoku-pulumi-state-<suffix>
pulumi stack init prod --secrets-provider=passphrase
pulumi stack import --file bootstrap-state-backup.json
pulumi refresh               # MUST report no changes
```

This is a one-time procedure, not a standing manual step: every later `pulumi up` runs straight
against the GCS backend. The bucket has object versioning from birth, so the migration is
recoverable; the documented fallback if export/import misbehaves is to create the bucket with
three `gcloud` commands and `pulumi import` it.

## CI/CD

`.github/workflows/ci-deploy.yml` is the single entry point; `deploy-gcp.yml` becomes
`on: workflow_call` only, with its `push` and `workflow_dispatch` triggers removed. Target
selection is documented in `docs/llds/cloud-platform.md` — *Deploy Target Selection*.

The eight phased `workflow_dispatch` inputs (`deploy_cloud_run`, `enable_coach`,
`coach_ai_provider`, …) are removed. They existed to bring production up in stages against an
unbuilt project; keeping them would preserve exactly the foot-gun that let a manual dispatch
silently revert the Vertex cutover. The GCP deploy becomes all-or-nothing, like the AWS one.

`GCP_PROJECT_ID` moves from a **secret** to a repository **variable**. It is not sensitive — it
appears in every public Cloud Run URL and in the Firebase config the SPA ships — and making it a
secret is what forced the current workflow to pass only image *tags* between jobs (GitHub scrubs
secret-bearing outputs) and reassemble full URIs in-job. That workaround disappears.

Concurrency: `ci-deploy.yml` keeps `group: ci-deploy-${{ github.ref }}`; the GCP reusable
workflow adds `group: gcp-${{ inputs.stack }}` so two runs cannot contend for one Pulumi state
lock.

## Testing

`pulumi.runtime.set_mocks()` lets every component be instantiated and asserted on with no cloud
contact. This is the first infrastructure unit-test layer in the repository — the AWS facet's
"no Terratest or equivalent" gap has stood because HCL offers no cheap seam. @spec CP-PUL-081

Acceptance layers: `ruff` + `pytest` statically; `test_no_authoritative_iam.py` structurally;
`pulumi preview` on every PR touching `infra/gcp/**`; a second `pulumi up` reporting `0 changed`
as a drift signal; and the existing smoke scripts at runtime. @spec CP-PUL-082

## Design Decisions

| Decision | Chosen | Alternatives Considered | Rationale |
| --- | --- | --- | --- |
| IaC tool | Pulumi (Python, uv) | Stay on Terraform; OpenTofu; CDKTF | A general-purpose language resolves the apply-time-value problems that force post-apply scripts on AWS, and `ComponentResource` collapses two near-identical Cloud Run `.tf` files into one class. Learning value was an explicit goal. |
| Migration style | Clean-room new project | `pulumi import` of the 11 live resources | Import requires the initial code to match live state exactly, which would let the migration shape the code. A clean room also proves the bootstrap path end-to-end. |
| Identity provisioning | Two-stack split, all IaC | Manual bash (the previous tenet); one all-powerful stack | Revises the HLD tenet. Splitting the identity beats removing it from automation: CI gains one role, loses Secret Manager, and gains no billing reach. |
| State backend | Self-managed GCS + Cloud KMS | Pulumi Cloud free tier | Pulumi Cloud has better DX but puts a third-party SaaS in the critical deploy path, against the self-sufficiency goal. Mirrors the existing GCS Terraform backend. |
| Budget placement | `bootstrap` stack | `app` stack | The budget is billing-account-scoped; a repo-federated CI identity must not hold write access across every project on the account. |
| DNS | Cloud DNS zone for a delegated subdomain | Keep the Route53 CNAME; move the apex to Cloud DNS | One manual NS record buys complete steady-state independence. Moving the apex would invert the coupling rather than remove it, and the apex serves more than this app. |
| Data migration | Start empty | `gcloud firestore export`/`import` | Identity continuity is automatic via the Google `sub`, so the cost is history and leaderboard only, and the old project is retained 30 days if an export is later wanted. |
| Deploy target | Repo variable governing `main` only | Also switch canonical DNS; also drive local dev defaults | Deploy selection is reversible and has no DNS race. A traffic failover is a different feature with a different risk profile. |

## Technical Debt & Inconsistencies

- `infra/gcp/main.tf:3` defines `is_rc = startswith(terraform.workspace, "rc-")`, which **never
  matches** — GCP workspaces derive from `rcg-*` branches. `coach_bedrock_api_mode` was therefore
  permanently `"invoke"` on GCP and the converse A/B never ran there. Low impact (the A/B
  resolved to `invoke` anyway) but the Pulumi port must not reproduce it.
- `scripts/infra/gcp/bootstrap.sh` creates an `sudoku-image-recognition` Artifact Registry
  repository that CI has never used; both images go to `sudoku-backend`. The Pulumi facet creates
  one repository.
- The coach's JSON output schema is duplicated between `CoachPromptBuilder.OUTPUT_SCHEMA_JSON`
  and `VertexCoachClient.RESPONSE_SCHEMA` — a drift risk owned by the AI Coach arrow, not here.

## Open Questions

- ~~The `StackReference` name format on a self-managed GCS backend.~~ **Resolved 2026-09-13**:
  DIY backends place every project under a virtual organization named by the literal constant
  `organization`, so the form is `organization/<project>/<stack>` — not an account name, and not
  the bare stack name that `pulumi stack ls` displays. Requires the project-scoped state layout,
  which this backend uses (`.pulumi/stacks/<project>/<stack>.json`).
- ~~Whether `gcp.identityplatform.Config` can *initialise* the Identity Platform entitlement.~~
  **Resolved 2026-09-13: yes.** Applied to a project that had never had Identity Platform
  enabled, and it created the config, the Google IdP and the authorised-domain list without any
  console step. The "Enable Identity Platform" click in the old runbook is gone, and
  `scripts/infra/gcp/identity-platform-bootstrap.sh` is redundant.

  Two caveats found while doing it. `identitytoolkit.googleapis.com` **requires a quota project**,
  so under human ADC the call 403s with a misleading `SERVICE_DISABLED` — the second API to need
  the scoped-provider treatment after the billing budget, which makes it a pattern rather than a
  one-off (see *Quota-project APIs* below). And the server populates `signIn.phoneNumber` whether
  or not it is declared, so omitting it makes `pulumi refresh` report drift on every run; it is
  declared explicitly for that reason.

### Quota-project APIs

Some Google APIs refuse a call that carries no quota project. Service-account credentials supply
one implicitly, so **CI never sees this** — it appears only when a human runs a stack with their
own Application Default Credentials, and the error names `SERVICE_DISABLED` rather than the
actual cause, pointing at an API that is in fact enabled.

Known so far: `billingbudgets.googleapis.com`, `identitytoolkit.googleapis.com`.

The fix is a **dedicated `gcp.Provider`** with `user_project_override=True` and `billing_project`,
attached to those resources alone. Do **not** set it stack-wide: a global override makes every
call send the user-project header, which then requires `serviceusage.googleapis.com` on the target
project and breaks `gcp.projects.Service` before it can enable anything. That mistake cost an
apply during Phase 2.
- Whether Firebase custom-domain verification for this subdomain wants a CNAME or A+TXT records.
  The known-working CNAME is hard-coded; driving records off `required_dns_updates` is an
  elegant-but-fragile follow-up.
- `CP-GCP-061` (budget hard enforcement) and `CP-GCP-091` (private VPC egress to Firestore)
  remain deferred.
- `UM-GCP-008` — GCP admin authorization. Identity Platform has no group concept; a Firebase
  custom claim or a configured allowlist is the likely answer. Owned by User Management.

## References

- HLD: `docs/high-level-design.md` — *Multi-Cloud Deployment*, *Tenets*
- Sibling LLD: `docs/llds/cloud-platform.md` (AWS facet + cloud-general)
- EARS: `docs/specs/cloud-platform-specs.md` — `CP-GCP-*`, `CP-PUL-*`
- Plan: `docs/planning/gcp-pulumi-replatform.md`, and the per-phase plans beside it
- Runbook: `docs/runbooks/gcp-manual-setup.md`
- Frontend coupling: `docs/llds/react-frontend.md` (`VITE_AUTH_PROVIDER`, `VITE_FIREBASE_*`)
- Auth model: `docs/llds/user-management.md` (in-app JWT validation, Google-`sub` identity)
- Depends on: nothing (provisions all other components)
- Depended on by: all components (runtime environment)
