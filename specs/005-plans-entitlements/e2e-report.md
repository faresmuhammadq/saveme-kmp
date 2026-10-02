# E2E report: 005-plans-entitlements

**Run**: 2026-10-02 · device emulator-5554 (API 37, foldable 2076×2152) · saveme-kmp@9ec61e5 + spec 005 working tree · saveme-backend@a42f5d0 (+ another session's uncommitted sync work, migrations applied)
**Result**: 11 passed · 0 failed · 7 manual · 18 scenarios

| Scenario | Flow | Result | Notes |
|---|---|---|---|
| US1-S1 | US1-S1-free-answer-on-sign-in.yaml | pass | read on the Plan screen (the gate's source) |
| US1-S2 | — | manual | not device-reachable: the seed has no unlimited quota besides `wallets.max`, already shown as "Unlimited wallets"; proven by `FeatureValueMappingTest` / gate tests |
| US1-S3 | US1-S3-refresh-after-operator-grant.part1/part2.yaml | pass | two parts, operator grant between, `plan.refresh` |
| US1-S4 | — | manual | offline toggling; proven by unit tests (cached answer before network); also spot check 3 below |
| US1-S5 | — | manual | needs an answer omitting a capability; proven by `FeatureValueMappingTest` |
| US1-S6 | — | manual | not-built: no client feature issues a gated write yet; proven by `AuthErrorCopyTest` + refresher trigger tests |
| US2-S1 | US2-S1-free-plan-no-dates.yaml | pass | |
| US2-S2 | US2-S2-active-subscription.yaml | pass | manual grant does not renew → "Active · Ends on …" |
| US2-S3 | US2-S3-grace-period.yaml | pass | seed `+plus-grace` (row updated to grace, 3 days) |
| US2-S4 | US2-S4-no-end-date.yaml | pass | seed `+plus-lifetime` |
| US2-S5 | US2-S5-granted-while-app-open.part1/part2.yaml | pass | the brief's checkpoint: Free → grant → refresh shows Plus, no sign-in |
| US2-S6 | — | manual | offline; proven by Plan reducer/ViewModel tests; spot check 3 below |
| US3-S1 | US3-S1-catalogue-in-order.yaml | pass | |
| US3-S2 | US3-S2-current-plan-marked-in-text.yaml | pass | |
| US3-S3 | US3-S3-no-price-without-google-play.yaml | pass | seed has InstaPay prices only; Play-price case in `CataloguePlanTest` |
| US3-S4 | US3-S4-names-follow-language.yaml | pass | |
| US3-S5 | — | manual | needs a retired plan; proven by Plan ViewModel test |
| US3-S6 | — | manual | needs a catalogue that never loaded; proven by Plan reducer test + screenshot |

## Failures found and fixed during the run

| Run | Scenario(s) | Side | Cause | Fix |
|---|---|---|---|---|
| 1 | US1-S3 p2, US2-S2/S3/S4, US2-S5 p2, US3-S2 | FAIL (flow) — runner | `grant_plus` passed `'e2e-operator'` as operator id; the backend's `audit_entries.actor_id` is a uuid | runner uses a fixed UUID operator (`00000000-0000-7000-8000-0000000000e2`) and looks the plan up by `code = plus_monthly` |
| 1 | US3-S1, US3-S3 | FAIL (flow) | third card is below the fold on the device | `scrollUntilVisible` before card 2 assertions (also US3-S2, US3-S4) |
| 2 | US3-S1 (and US3-S4) | FAIL (flow) | `id: plan.card.N` + `text:` never matches: the card node has no text, its children do | `text` + `childOf: { id: plan.card.N }` |
| 2 | US1-S3 p2 | FAIL (flow) | `assertNotVisible "Sharing: not included"` also matched the catalogue's Free card | capability assertions scoped to the current-plan section with `above: "Plans"` (US1-S1, US1-S3, US2-S1, US2-S3 too) |
| 2 | US3-S3, US3-S4 | FAIL (env) | backend dev container ran another session's uncommitted sync code needing 4 pending migrations (`users.change_seq` missing) | waited ~25 min, then applied the pending migrations to the dev DB |
| QA | — | app (visual) | catalogue cards drew a check mark beside "Sharing: not included" / "…: not included" | `PlanCapability(text, included)`; excluded lines draw a dash (`AppIcons.Minus`) in the caption tint; screenshots re-recorded |

## Manual spot checks (quickstart §3)

| # | Result | Evidence |
|---|---|---|
| 1 | pass | Free, "Sharing: not included", "Unlimited wallets", no dates, catalogue Free / Plus · Monthly / Plus · Yearly, Free marked current, no prices (E2E US2-S1, US3-S1..S3) |
| 2 | pass | after grant: "Plus · Monthly", "Active · Ends on 1 Nov 2026" (30 days), "Share wallets with up to 5 people" |
| 3 | pass | offline simulated by removing `adb reverse` (airplane mode does not cut the adb-reverse loopback) + kill/relaunch: Plus shown from cache with the stale note; refresh shows the note again; still signed in |
| 4 | pass | Arabic: مجاني, بلس · شهري, بلس · سنوي, mirrored layout, Western digits ("5 أشخاص"); 200% font in Arabic: no clipping or overlap |
| 5 | pass | sign out of the Plus person, sign in as another: "Free", no status line, no stale note, Free card marked current |

## Regression (flows that open Account)

Account gained a "Plan" row above "Devices signed in"; these flows open Account and were rerun on
the same build:

- `002-account-security` (all 26 flows): 24 pass. US3-S1 and US3-S6 fail exactly as recorded in
  `specs/002-account-security/e2e-report.md` (pre-existing FAIL (app): `AppSelectField` hides its
  value from accessibility; budget-day "Applies from" note). Not caused by this feature.
- `001-design-system-auth` US2-S5 (session ended elsewhere): pass.
- `003-auth-completion` US3-S4 (reset ends this phone's session): pass.
