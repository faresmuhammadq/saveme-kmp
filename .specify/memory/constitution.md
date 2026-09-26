# Mobile App Constitution

<!-- The product codename appears in this file only as display text; see P-11. -->

**Version**: 1.0.0 | **Ratified**: 2026-09-26 | **Last Amended**: 2026-09-26

## Purpose & Precedence

This constitution governs every feature spec, plan, implementation and review in this Kotlin
Multiplatform client repository (`:shared`, `:androidApp`, `iosApp`).

1. **External contract first.** The backend's `docs/openapi/`, `docs/api-conventions.md`,
   `docs/security-baseline.md` and `docs/glossary.md` (repository `saveme-backend`) define the
   API and the domain vocabulary. This constitution MUST NOT contradict them; a contradiction is
   a defect to fix in the same change.
2. **Product-wide rules are mirrored.** Principles marked *Mirrors backend* restate a rule of the
   backend constitution (v1.3.1). A change to that backend rule requires a matching amendment
   here, and the reverse.
3. This document **overrides** conflicting guidance in `CLAUDE.md`, `AGENTS.md`, older plans and
   agent habits. It does not override the law, Google Play policy, or an explicit instruction
   from the repository owner.
4. Every feature plan MUST complete a [Constitution Check](#constitution-check) before design and
   again after design.
5. A rule that cannot be checked as yes or no is a defect in this document. Report it and amend.

## Core Principles

Each principle has a stable ID that is never reused or renumbered. **Non-negotiable** principles
encode locked product decisions: a plan that fails one is blocked until the plan changes or this
constitution is amended.

### P-01 Ubiquitous language

**Status**: Active | **Non-negotiable**: yes | **Mirrors backend**: P-01

1. Domain terms in code, UI copy and translation files MUST match the backend glossary
   ("Wallet" / محفظة, "Transaction", "Budget", "Device Session", …).
2. Synonyms for a glossary term ("account", "purse", "fund" for Wallet) MUST NOT be introduced.
3. "Token" and "PAT" MUST NOT appear in user-facing copy; the UI says "session" or "device".

*Rationale*: one word per concept across backend, app and translations removes a class of
modelling, translation and support confusion.

### P-02 API contract fidelity

**Status**: Active | **Non-negotiable**: yes | **Mirrors backend**: api-conventions

1. The OpenAPI documents are the source of truth for every request and response shape.
2. Request DTOs MUST carry only members declared in the schema (unknown members are refused
   server-side).
3. Response decoding MUST ignore unknown members.
4. Client behaviour on failure MUST branch on the problem `code`. It MUST NOT branch on
   `detail`, `title`, or the HTTP status alone.
5. Every request MUST send `X-Client-Platform`, `X-Client-Version` (semver), `Accept-Language`
   and `X-Correlation-Id`.
6. Automatic retries MUST follow the error catalogue's `retryable` class and honour
   `Retry-After`. Credential submissions MUST NOT be retried automatically.

*Rationale*: a client that drifts from the contract fails silently in the field; branching on
codes is the only behaviour that survives copy edits and Arabic responses.

### P-03 Money

**Status**: Active | **Non-negotiable**: yes | **Mirrors backend**: P-03

1. Money MUST be an integer amount in minor units (`Long`) plus an ISO 4217 alpha-3 code,
   through one shared `Money` type.
2. `Float`, `Double` and `BigDecimal` MUST NOT hold monetary values in storage, DTOs, state or
   arithmetic.
3. Fraction digits MUST come from the currency (EGP 2, KWD 3), never a hard-coded 2.
4. Negative amounts MUST render with U+2212; digits MUST be tabular.
5. Expenses MUST render in neutral ink, never in the error colour; liabilities MUST render as an
   amount owed.

*Rationale*: floats lose money silently, and the loss is found by users, not tests.

### P-04 Offline-first

**Status**: Active | **Non-negotiable**: yes | **Mirrors backend**: P-06, P-07

1. For every syncable entity, the on-device database MUST be the UI's source of truth; screens
   MUST NOT render syncable data directly from a network response.
2. Creates and edits of syncable entities MUST be written locally first and synced afterwards;
   a missing connection MUST NOT block them.
3. Syncable entities MUST be created with client-generated UUIDv7 identifiers.
4. Every sync write MUST carry the last-seen `version`. A `VERSION_CONFLICT` MUST be resolved by
   the client (merge or user choice) and MUST NOT be resolved by silently overwriting the server.
5. Deletion of a syncable entity MUST be a local tombstone synced to the server, never a hard
   delete.
6. Auth and account data (User, credentials) are not syncable and are exempt from rules 1–5.

*Rationale*: the product is a manual-entry finance app used on patchy mobile networks; a lost
edit destroys trust, while a visible conflict is recoverable.

### P-05 Time

**Status**: Active | **Non-negotiable**: yes | **Mirrors backend**: P-12

1. Instants MUST be held as UTC `Instant` values.
2. Calendar dates (transaction date, period start) MUST be held as `LocalDate` with no zone.
3. The user's timezone MUST be applied only when displaying, never when writing.

*Rationale*: Egypt's DST history makes anything but UTC plus calendar dates report the wrong
month to someone.

### P-06 Localisation & RTL

**Status**: Active | **Non-negotiable**: yes | **Mirrors backend**: P-14

1. All user-facing text MUST come from Arabic and English string resources; none is hardcoded.
2. Numerals MUST be Western (0–9) in both languages.
3. Every screen and component MUST mirror correctly in RTL (navigation, chevrons, back arrows,
   progress direction); numbers render LTR inside RTL runs.
4. Arabic text MUST use line height 1.6, no letter-spacing and no forced uppercase.
5. Server `detail` strings MUST NOT be shown; copy is chosen by error `code` (P-02).

*Rationale*: Arabic is the primary market language; Western numerals are what users expect in
financial figures.

### P-07 Design system only

**Status**: Active | **Non-negotiable**: yes | **Mirrors backend**: —

1. Feature screens MUST be built from `designsystem` components and tokens.
2. Feature code MUST NOT declare raw colours, font sizes, font families, or spacing/size values
   that duplicate a token.
3. A visual need not met by the library MUST be added to `designsystem` first (with catalogue
   entry and tests), checked against the design canvas "Save Me Mobile Design System".

*Rationale*: one source of visual truth keeps every screen consistent and makes a future dark
theme a token change, not a rewrite.

### P-08 Accessibility

**Status**: Active | **Non-negotiable**: no | **Mirrors backend**: —

1. Every interactive element MUST have a touch target of at least 48×48 dp.
2. Icon-only controls MUST have a content description.
3. State MUST NOT be conveyed by colour alone.
4. Body text MUST meet 4.5:1 contrast (3:1 at 24 sp and above).
5. Screens MUST remain usable at 200% system font scale without clipped text.

*Rationale*: financial tools are used by everyone, including people with low vision and motor
impairments; these rules are cheap when built in and expensive to retrofit.

### P-09 Security & privacy

**Status**: Active | **Non-negotiable**: yes | **Mirrors backend**: P-15 rule 4, security baseline

1. The session credential MUST be stored only in Keystore- (Android) or Keychain- (iOS) backed
   encrypted storage and MUST be excluded from backup and device transfer.
2. Credentials, mailed secrets, amounts, notes and full financial payloads MUST NOT be logged,
   sent to crash reporting or analytics, or shown in UI.
3. Cleartext HTTP MUST be permitted only in the development build flavor.
4. The client MUST NOT undermine uniform answers: it MUST NOT reveal or hint whether an email
   address has an account.
5. Any 401 on a credentialed request MUST clear the local session.

*Rationale*: the device holds a live credential to someone's finances; a leak through logs,
backups or UI is as damaging as a server breach.

### P-10 Shared-first architecture

**Status**: Active | **Non-negotiable**: yes | **Mirrors backend**: P-02 (in spirit)

1. Business logic, state holders, networking and persistence logic MUST live in `commonMain`.
2. Platform code MUST sit behind an `expect`/`actual` declaration or an interface implemented
   per platform; `commonMain` MUST NOT reference Android or iOS APIs.
3. Dependencies MUST flow `ui → domain → data`. `domain` MUST NOT import Ktor, Compose, or
   database libraries.
4. A feature package MUST NOT import another feature's `data` or `ui` package; cross-feature
   access goes through the other feature's `domain` API.
5. `designsystem` MUST NOT depend on any `feature` package or `core.network`.

*Rationale*: boundaries written as import rules can be checked by a test instead of memory, and
keep iOS a compile target rather than a rewrite.

### P-11 Naming

**Status**: Active | **Non-negotiable**: yes | **Mirrors backend**: P-13

1. The product codename MUST appear only in string resources and build configuration display
   values (app label).
2. It MUST NOT appear in class, object, function or composable names, Gradle module names, or
   any new package segment.
3. *Grandfathered*: the existing root package and applicationId `com.fares.saveme` are exempt;
   they MUST NOT be copied into new identifiers.

*Rationale*: the commercial name is undecided. A display string is trivial to rename; symbols
across a backend, an app and a landing page are not. The applicationId is exempt because it is
immutable once published and the app is not yet published under any other.

### P-12 Entitlements & billing

**Status**: Active | **Non-negotiable**: yes | **Mirrors backend**: P-09, P-10

1. Capability gating MUST read the entitlement `features` flags, never the plan label.
2. In-app purchases MUST use Google Play Billing.
3. The in-app billing surface MUST expose only "restore purchases" and "enter code".
4. InstaPay claims MUST NOT be submitted from inside the app.

*Rationale*: pricing changes more often than code, and Google Play policy violations remove the
app from the store.

### P-13 Observability

**Status**: Active | **Non-negotiable**: no | **Mirrors backend**: P-15

1. Every request MUST carry a fresh UUIDv7 `X-Correlation-Id`.
2. Unexpected errors MUST show the server's `request_id` so it can be quoted to support.
3. Crash reports and analytics MUST NOT carry PII, amounts or notes (P-09).

*Rationale*: "my transfer vanished" is unanswerable without an id joining app and server logs.

### P-14 Quality gates

**Status**: Active | **Non-negotiable**: no | **Mirrors backend**: P-16

1. The build and `./gradlew allTests` MUST pass before merge.
2. Every invariant a feature introduces MUST have a test that fails when it is broken.
3. Every consumed endpoint MUST have contract tests (mock HTTP engine) covering its request
   shape and each error code the feature handles.
4. Every `designsystem` component MUST have screenshot tests in English LTR, Arabic RTL and
   font scale 2.0.
5. No new dependency without the repository owner's approval, recorded in the plan.

*Rationale*: the rules above erode quietly without tests; screenshot tests are the only practical
guard for RTL and font scaling.

### P-15 Scope guard

**Status**: Active | **Non-negotiable**: yes | **Mirrors backend**: P-17

1. A plan MUST NOT introduce a capability listed in [MVP Scope Guard](#mvp-scope-guard) without
   amending this constitution first.

*Rationale*: the risk is not building the wrong thing but building too many things before
shipping the first one.

## Architecture & Packages

All code under root package `com.fares.saveme` (grandfathered, P-11).

| Package | Owns | May depend on |
|---|---|---|
| `designsystem` | tokens, theme, components, money formatting, component catalogue | Compose only |
| `core` | config, platform info, network client, error model, storage, DI | — |
| `feature.<context>` (`data`, `domain`, `ui`) | one bounded context each, named as in the backend (identity/auth, wallets, …) | `core`, `designsystem`, other features' `domain` |
| `navigation` | routes, back stacks, deep-link intake | features' `ui` entry points |
| `devtools` | development-only screens (catalogue entry, paste link) | anything; excluded from release builds |

`:androidApp` is a thin host: flavors, manifest, intent filters, platform wiring. `iosApp` MUST
compile; it receives no iOS-specific features in MVP (P-15).

## MVP Scope Guard

Out of scope for MVP (mirrors the backend list). Introducing any requires an amendment (P-15):

- bank synchronisation or SMS parsing
- multi-currency transfers and FX
- savings goals
- bill reminders beyond recurring occurrences
- AI features (assisted entry, "talk to your data")
- iOS-specific work, including live iOS sign-in until the API accepts an `ios` platform
- web checkout with a registered payment provider
- refunds automation
- dark mode (tokens MUST stay role-based so it can be added later)

## Quality Gates

A change is mergeable when all hold:

1. `./gradlew build allTests` passes, including screenshot verification.
2. Each new invariant has a failing-when-broken test (P-14.2).
3. Contract tests cover every new or changed endpoint use, including its error codes (P-14.3).
4. The plan contains a complete Constitution Check for both gates.
5. The release variant contains no cleartext permission, no dev-only screens, no `http` link
   filters (P-09.3).
6. No new dependency without owner approval.

## Constitution Check

Every `plan.md` includes this section, filled in before Phase 0 and again after Phase 1.

```markdown
## Constitution Check

*Constitution v1.0.0. GATE: before Phase 0 and again after Phase 1.*

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | … |
| … one row per Active principle … | | |
```

Rules:

1. Result is `PASS`, `FAIL` or `N-A`. `N-A` requires a one-line reason.
2. A `FAIL` on a non-negotiable principle **blocks** the plan: change the plan or amend first.
3. A `FAIL` on any other principle requires a row in the plan's Complexity Tracking table.
4. Evidence cites concrete plan elements (package, screen, endpoint), not a restatement.
5. Pre-design and post-design results are both kept.

## Governance

**Precedence**: see [Purpose & Precedence](#purpose--precedence).

**Amendments**: a pull request that changes this file and states what changes, why, the version
bump, and — for a *Mirrors backend* principle — the matching backend amendment. The repository
owner ratifies. In-flight plans keep the version they were checked against.

**Versioning** (semantic):

| Bump | When |
|---|---|
| MAJOR | a principle is retired or its meaning redefined |
| MINOR | a principle is added or materially expanded |
| PATCH | wording, formatting or clarification with no change in meaning |

Every amendment updates **Last Amended** and the Sync Impact Report comment at the top.

**Principle IDs**: `P-NN` IDs are permanent. A retired principle keeps its ID with
`**Status**: Retired (vX.Y.Z)` and a pointer to its replacement. IDs are never reused.
Client IDs are independent of backend IDs; the *Mirrors backend* line records the mapping.

**Consistency**: run `/speckit-analyze` before implementing each feature. Contradictions between
this constitution, the backend contract and a feature's spec are defects to resolve before code.

**Version**: 1.0.0 | **Ratified**: 2026-09-26 | **Last Amended**: 2026-09-26
