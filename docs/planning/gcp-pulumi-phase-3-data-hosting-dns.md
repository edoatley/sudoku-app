# Implementation Plan: GCP Pulumi — Phase 3, App Stack (Data, Hosting, DNS)

**Status**: Complete — 2026-09-13
**Created**: 2026-09-13
**Origin**: `docs/planning/gcp-pulumi-replatform.md` §4
**Arrow**: `cloud-platform` (`docs/arrows/cloud-platform.md`) — GCP facet

---

## 1. Goal

Stand up the **`app` stack** on the new project: Firestore, the Firebase Hosting site, and the
Cloud DNS managed zone. Delegate `gcp.edoatley.co.uk` from Route53 once, by hand.

**No compute and no auth yet.** Cloud Run is Phase 6, Identity Platform is Phase 4, and attaching
the Hosting custom domain is Phase 8 — deliberately, so the NS delegation has days to propagate
before anything depends on it.

This is the first time the `app` stack runs at all, so it is really three firsts: the first
`StackReference`, the first use of the KMS secrets provider, and the first resources created by
something other than the human-run bootstrap.

## 2. What Phase 2 taught us, applied here

Five apply attempts, five environmental failures, none a logic error and none visible to
`preview`. Carried forward:

| Lesson | Applied |
| --- | --- |
| Missing API enablement fails mid-apply, never at plan time | Every API this phase needs (`firestore`, `firebase`, `firebasehosting`, `dns`) is already enabled by bootstrap and asserted by `test_every_resource_kind_has_its_api_enabled`. Verify with `gcloud services list` before applying. |
| `depends_on` does not reach a component's children | Components merge caller opts; `test_child_opts_propagation` enforces it. The app stack has no API enablement of its own, so ordering is simpler — but the same rule holds for the Hosting-site → custom-domain chain. |
| A stack-wide provider setting can break unrelated resources | The billing quota override stays scoped to the bootstrap budget. This stack adds no global provider config. |
| Values must match the account, not the docs | Firestore location is `us-central1`, matching the bootstrap region and `CP-GCP-024`. |
| Resources can be creatable but not readable | Run `pulumi refresh` before declaring done — that is what caught the missing `monitoring` API. |

## 3. Stack setup

The app stack is a **separate Pulumi project** (`sudoku-gcp-app`) in the same GCS backend, using
the **KMS key** bootstrap created — no passphrase:

```bash
cd infra/gcp/app
pulumi stack init prod \
  --secrets-provider="$(cd ../bootstrap && pulumi stack output kms_key_uri)"
```

The `StackReference` name on a DIY backend is `organization/<project>/<stack>`, where
`organization` is a **literal constant**, not an account name:

```python
bootstrap = pulumi.StackReference("organization/sudoku-gcp-bootstrap/prod")
```

Verified 2026-09-13: the backend uses the project-scoped layout this requires
(`.pulumi/stacks/sudoku-gcp-bootstrap/prod.json`).

## 4. Work items

- [x] **3a. Initialise the `app` stack** with the KMS secrets provider (§3), and
      `infra/gcp/app/Pulumi.prod.yaml` carrying `projectId`, `region`, `customDomain`,
      `dnsZoneDomain`, and `bootstrapStack`.
      @spec CP-PUL-041
- [x] **3b. Wire the `StackReference`** and export a stack output for every bootstrap value the
      later phases consume, so Phase 6 does not have to re-derive them.
      @spec CP-PUL-021
- [x] **3c. `FirestoreDatabase`** — `(default)` database, `FIRESTORE_NATIVE`, `us-central1`, PITR
      and delete protection on (prod), the `coachRateLimits.expiresAt` TTL, and the
      `games(userId ASC, status ASC, endedAt DESC)` composite index. `protect=True`.
      @spec CP-GCP-020, CP-GCP-022, CP-GCP-023, CP-GCP-024, CP-PUL-022
- [x] **3d. `StaticSite`** — Firebase project enrolment and the Hosting site
      (`sudoku-eo-2026`). **No custom domain yet** (`custom_domain=None`).
      @spec CP-GCP-040
- [x] **3e. `DnsZone`** — managed zone for `gcp.edoatley.co.uk.`, no records yet. Export
      `name_servers`.
      @spec CP-PUL-050
- [x] **3f. Delegate the subdomain** — take `pulumi stack output name_servers` and add the NS
      record in the Route53 `edoatley.co.uk` zone (`Z055000739D7L0ZGFAMC1`, AWS `backups`
      profile). **Manual, one-time, and the only AWS touch in this phase.** Extend
      `scripts/infra/shared/delegate-dns.sh` rather than writing a new script.
- [x] **3g. Verify propagation** — `dig +trace NS gcp.edoatley.co.uk` returns the four Google
      name servers. This can take minutes to hours; it does not block the rest of the phase.
- [x] **3h. Unit tests** for the app stack's wiring, following the bootstrap pattern: assert the
      Firestore database is protected in prod and disposable otherwise, that the Hosting site id
      respects the 30-char cap, and that no custom domain is attached at this phase.
      @spec CP-PUL-081

## 5. Definition of Done

- [x] `pulumi up` on `sudoku-gcp-app/prod` succeeds
- [x] `pulumi refresh` reports **no changes** (11 unchanged)
- [x] A second `pulumi up` reports `0 changed` (10 unchanged)
- [x] Firestore `(default)` exists, `FIRESTORE_NATIVE`, `us-central1`, PITR + delete protection on
- [x] Composite index `READY`; the `coachRateLimits.expiresAt` TTL is `ACTIVE`. (Read the TTL
      from `pulumi stack export` — `gcloud firestore indexes fields list` returns the wildcard
      field config, not the specific one, and looks empty.)
- [x] `https://sudoku-eo-2026.web.app` responds — Firebase's **"Site Not Found"** page, which
      is the correct state for a site with no release. The frontend deploy is Phase 6.
- [x] `dig NS gcp.edoatley.co.uk @8.8.8.8` returns four `*.googledomains.com.` servers, and
      `dig +trace` shows the handoff (TTL 300 from Route53, 21600 from Cloud DNS). Cloud DNS
      answers with the `aa` flag and its own SOA.
- [x] `make gcp-inventory` shows no unexpected unmanaged IAM bindings
- [x] `CP-PUL-022` and `-050` flipped to `[x]`, along with the rest satisfied by Phases 1-3
- [x] The **old project keeps serving production** — unchanged throughout
- [x] Bootstrap stack untouched — `pulumi preview` reports 53 unchanged

## 6. Risks specific to this phase

| Risk | Mitigation |
| --- | --- |
| **The `StackReference` format is wrong for a DIY backend** and the app stack cannot read bootstrap's outputs | Resolved in advance: `organization/<project>/<stack>`, `organization` literal. If it still fails, the fallback is duplicating the values into `Pulumi.prod.yaml` — ugly, and it would mean losing the cross-stack wiring that justifies the two-stack split, so fix the reference rather than work around it. |
| **Firestore location is irreversible** — a database cannot be moved, only deleted and recreated | `us-central1`, matching bootstrap and `CP-GCP-024`. Checked before apply, because `protect=True` then makes it deliberately hard to undo. |
| **The NS delegation needs the AWS `backups` profile**, a different account from the sandbox | Confirm `aws sts get-caller-identity --profile backups` before starting 3f. This is the last AWS dependency in the GCP path and it is one record, once. |
| **DNS propagation is not instant** | 3g does not block the phase. The custom domain is deliberately Phase 8, giving days of slack. |
| **Firebase project enrolment is not cleanly reversible** — `gcp.firebase.Project` adds Firebase to a GCP project and un-enrolling is awkward | Acceptable: the project exists to run this app. Noted so it is a known one-way door rather than a surprise. |

## 7. Out of scope

- Cloud Run services and the frontend build (Phase 6)
- Identity Platform and the OAuth client (Phase 4)
- Attaching the Hosting custom domain and the `sudoku.gcp.edoatley.co.uk` record (Phase 8)
- Ephemeral `rcg-*` stacks (Phase 4's stack strategy work)
- Any change to the old project, which keeps serving production until cutover
