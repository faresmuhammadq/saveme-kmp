# Summary: Plans & Entitlements (spec 005, C3)

**Date**: 2026-10-02 · **Status**: implemented, E2E green, committed on branch `005-plans-entitlements` (a3a4d98) · **Backend contract**: `saveme-backend@055afa1`, rechecked against `2487f10`.

## What was built

- **Entitlements gate**: `Entitlements.can(flag)` and `quota(quota)` read the cached account snapshot.
  - A `null` quota means unlimited. This fixes the old cache, which read it as 0 and would have blocked wallet creation once C4 checks `wallets.max`.
  - A missing or wrong-typed value means off or 0 (the backend's own rule).
  - `AccountSnapshot` gains `planId`.
- **Refresh triggers**: sign-in, foreground (skipped within 60 s of the last success), the Plan screen, a billing action, and `FEATURE_NOT_ENTITLED`. One refresh at a time; an answer arriving after sign-out or for another user is discarded; sign-out clears the stored subscription and catalogue.
- **`FEATURE_NOT_ENTITLED`** added to the client's error codes with en/ar copy; it triggers a refresh.
- **Plan screen**, from a new "Plan" row on Account:
  - Current plan with status and dates: "Active · Renews/Ends on …", "Payment due · Access until …", or "Active · No end date".
  - Stale note and refresh button.
  - Catalogue in order, names in the app's language, "Current plan" in text, Google Play price only, retry when it never loaded.
  - No purchase action and no mention of InstaPay.
- **`PlanCard`** design-system component: a check for included capabilities, a muted dash for those not included.
- **`run-e2e.sh`**: seeds `+plus`, `+plus-lifetime`, `+plus-grace`, and two-part flows with `e2e-between: grant-plus`.

## Results

- Host tests and Roborazzi checks pass (426 tests at implementation); `:androidApp:assembleDevDebug` and the iOS simulator compile pass.
- **E2E** (Maestro, emulator, local backend): 11/11 device-reachable scenarios pass, including the brief's checkpoint (Free → operator grant → refresh shows Plus without signing in again). 7 scenarios are manual and covered by unit and contract tests. Report: `e2e-report.md`.
- **Quickstart §3 spot checks**: 5/5 pass (Free, after grant, offline, Arabic at 200 % font, sign-out then another person).
- **Regression**: spec 002 Account flows 24/26 (US3-S1 and US3-S6 were already recorded app failures before this feature); spec 001 US2-S5 and spec 003 US3-S4 pass.
- **Fixed during QA**: check mark shown beside "not included" capabilities; E2E runner passed a non-UUID operator id to the backend audit log; flow selectors (below-the-fold card, `childOf` matching, current-plan scoping).

## Assumed defaults

Owner away with delegated authority; each the conservative option.

1. Only the Google Play list price is shown, never InstaPay (P-12.3/12.4, Play payment policy). None appear today because the seed has InstaPay prices only.
2. No "Restore purchases" or "Enter code" until C10 and C11; the screen reserves the place and exposes the refresh they will call.
3. A capability the answer doesn't mention, or a wrong-typed value, is off or 0; only an explicit `null` is unlimited.
4. The subscription's channel is not shown, so InstaPay is never named in the app.
5. The downgrade explanation (extra members become viewers) is left to C9.
6. One refresh at a time; a foreground refresh is skipped within 60 s of the last success; Plan-screen, billing-action and refusal refreshes always run.
7. The members-per-wallet quota is hidden while sharing is off ("Sharing: not included").
8. Entitlements stay in auth's account snapshot (no JSON shape change), refreshed through `SessionManager.refreshEntitlements()`; subscription and catalogue are `KeyValueStore` records; no Room change.
9. The Plan entry is on Account, not Home (Home was being changed by the sync-engine branch).
10. A new `feature.billing` package; `Money(amountMinor, currency)` lives there because the app had no shared money type.
11. A failed refresh shows the stale note; the "request id on unexpected refresh error" detail is not implemented because `RefreshOutcome` carries no request id.
12. `PlanNameNotGatedTest` allows `feature/auth/`, `feature/billing/ui/` and `billing/domain/PlanOverview.kt` to read plan fields.
13. `EmptyState` gained an optional `actionModifier` for the retry button's test tag.
14. E2E grace is set by updating the subscription row (no use case produces grace on demand); grants use a fixed-UUID operator (`00000000-0000-7000-8000-0000000000e2`) and look the plan up by `code = 'plus_monthly'`.
15. The offline spot check removes `adb reverse` instead of airplane mode (airplane mode does not cut the loopback route).

## Remaining risks

- Merge hotspots with the merged 004 sync engine: both strings files, `App.kt`, `AppModules.kt`, `ApiErrorCode`, `SessionManager`, `Routes`/`AppNavHost`, `run-e2e.sh` (all changes additive).
- No Google Play prices until B10; price display is only unit-tested.
- Capability icons are a fixed 20 dp and sit high next to text at 200 % font (cosmetic).
- Pre-existing spec 002 E2E failures (US3-S1, US3-S6) remain.
- `PlanCard` and the capability dash may not exist on the design canvas; owner design review suggested.
