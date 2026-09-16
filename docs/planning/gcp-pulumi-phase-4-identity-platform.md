# Implementation Plan: GCP Pulumi — Phase 4, Identity Platform

**Status**: Approved — ready to implement
**Created**: 2026-09-13
**Origin**: `docs/planning/gcp-pulumi-replatform.md` §4
**Arrow**: `cloud-platform` (`docs/arrows/cloud-platform.md`) — GCP facet

---

## 1. Goal

Provision Identity Platform as code on the new project, so a real Google user can sign in. This
reverses `CP-GCP-031`, which put the auth config outside IaC and made it two console clicks in a
runbook.

**This phase cannot be completed unattended.** Two of its steps have no API at all, and one of
them additionally blocks the old project's eventual deletion. Plan around that rather than
discovering it mid-phase.

## 2. The manual steps, and why they are irreducible

| Step | Why |
| --- | --- |
| OAuth consent screen | No GCP API configures it. |
| OAuth 2.0 Web client | **No GCP API creates OAuth client IDs.** True on every cloud. (`CP-PUL-032`, deferred permanently.) |
| Identity Platform smoke-test **user** | No IaC resource exists for a user. `scripts/infra/gcp/create-smoke-user.sh` is retained. (`CP-PUL-031`.) |

**The new project needs its own OAuth client.** The existing one lives in the *old* project and is
shared with AWS Cognito's Google federation — so deleting the old project at cutover+30d would
break AWS sign-in. A second client for Cognito is already backlog row 4 and gates that deletion.

## 3. The open question this phase answers

`gcp.identityplatform.Config` claims it can **initialise** the Identity Platform entitlement on a
project where `identitytoolkit.googleapis.com` is enabled and billing is linked. If true, the
"Enable Identity Platform" console click in the old runbook disappears entirely.

A clean-room project is the ideal place to find out, and it has never been tested here. **Do not
design around the optimistic answer.** Fallbacks, in order:

1. One documented console click, recorded as a manual step like the other two.
2. A `pulumi_command.local.Command` wrapping the same REST call `identity-platform-bootstrap.sh`
   makes, with a matching delete.

## 4. Work items

- [x] **4a. Configure the OAuth consent screen** on `sudoku-eo-2026` — external, `edoatley.co.uk`
      as an authorised domain, email/profile scopes only. At those scopes no Google verification
      review is required. *(Manual, console.)*
- [x] **4b. Create a Web OAuth 2.0 client** and add
      `https://sudoku-eo-2026.firebaseapp.com/__/auth/handler` to its authorised redirect URIs.
      *(Manual, console.)*
- [x] **4c. Store the client secret** — `pulumi config set --secret sudoku:googleOauthClientSecret`
      in the `app` stack. This is the **first real secret** behind the KMS key, and the first
      `secure:` value in either stack.
      @spec CP-PUL-041
- [x] **4d. Wire `IdentityPlatform`** into `app/__main__.py`: config, Google IdP, and an
      authorised-domain list built with `Output.all` over `localhost`,
      `<project>.firebaseapp.com`, `<project>.web.app` and the custom domain. An origin missing
      here fails sign-in at runtime with `redirect_uri_mismatch`.
      @spec CP-GCP-030, CP-PUL-030
- [x] **4e. Export `issuer` and `audience`** — the backend's `%gcp` profile hard-codes their
      shapes (`https://securetoken.google.com/<project>` and `<project>`), and a mismatch 401s
      every request. Export them so Phase 6 wires the Cloud Run env from the stack rather than
      by hand.
      @spec CP-GCP-011
- [x] **4f. Create the smoke-test user** — retarget `scripts/infra/gcp/create-smoke-user.sh` at
      the new project. CI authenticates it via `signInWithPassword`.
      @spec CP-GCP-032
- [ ] **4g. Verify identity continuity** *(see §5 — the load-bearing check)*
- [D] **4h. Delete `scripts/infra/gcp/identity-platform-bootstrap.sh`** — **deferred to Phase 9.**
      It is superseded by 4d, but `scripts/infra/gcp/bootstrap.sh` and
      `scripts/infra/shared/setup-local-secrets.sh` still call it, and both belong to the
      Terraform path that keeps the old project alive until cutover. Deleting it now would break
      a bootstrap that is still load-bearing. It goes with the other five scripts at Phase 9.
- [x] **4i. Unit tests** — the authorised-domain list contains all four origins; issuer and
      audience match the shapes `application.properties` expects; the client secret is marked
      secret and never exported.
      @spec CP-PUL-081

## 5. The check that actually matters

The whole clean-room approach rests on one claim: **a Google user signing into a brand-new
Identity Platform tenant keeps the same canonical `userId`**, because `UserIdentityResolver`
returns the raw Google `sub` rather than the Firebase UID, and Google's `sub` is stable per
account across OAuth clients.

That is cheap to verify and catastrophic to assume:

1. Sign in as a real Google user against the new project.
2. Decode the ID token and read `firebase.identities["google.com"][0]`.
3. Confirm it equals the `userId` already stored in the **old** project's Firestore `players`
   collection for that account.

If it does not match, the migration does strand every existing user, and the plan's "identity
continuity is automatic" premise is wrong — which would change the cutover story entirely.
**Do this before Phase 6, not at Phase 8.**

## 6. Definition of Done

- [x] `pulumi up` on the `app` stack succeeds with Identity Platform configured
- [x] `pulumi refresh` reports no changes; a second `up` reports `0 changed` (15 unchanged)
- [ ] A browser Google sign-in against `https://sudoku-eo-2026.web.app` completes without
      `redirect_uri_mismatch`
- [x] `signInWithPassword` against the smoke user returns an `idToken`
- [ ] **Identity continuity verified** per §5, with the two `userId` values recorded in the PR
- [x] The client secret is stored encrypted and does not appear in plaintext anywhere in the repo
- [x] `CP-GCP-030`, `CP-GCP-032`, `CP-PUL-030` flipped to `[x]`; `CP-GCP-031` struck through
- [x] Whether `identityplatform.Config` initialises the entitlement is recorded in the LLD —
      **it does**, so the old runbook's console click is gone
- [x] The old project still serves production, and its Cognito federation still works — the
      shared `SMOKE_TEST_USER_PASSWORD` secret was deliberately **not** overwritten

## 7. Risks

| Risk | Mitigation |
| --- | --- |
| **`identityplatform.Config` cannot initialise the entitlement** | §3's fallbacks. Either outcome gets recorded; the point is to stop guessing. |
| **The `sub` continuity assumption is wrong** | §5, run before Phase 6. If it fails, stop and re-plan the cutover — do not proceed to compute. |
| **A missing authorised domain** fails sign-in only at runtime, not at apply | Build the list from the same values Hosting uses, and test it. |
| **The OAuth client is a one-way door for the old project** | Creating a second client for Cognito (backlog row 4) is a prerequisite for deleting `sudoku-app-eo`, not an afterthought. |

## 8. Out of scope

- Cloud Run services and the frontend build (Phase 6)
- Repointing AWS Cognito at its own OAuth client (backlog row 4)
- GCP admin authorization — `UM-GCP-008`, deferred: Identity Platform has no group concept
