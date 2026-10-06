# Design Checklist: Reports (client)

**Purpose**: Unit tests for the requirements — completeness, clarity, consistency and coverage of
spec.md, plan.md and the contracts before tasks are generated (reviewer: implementer at PR time).
**Created**: 2026-10-06
**Feature**: [spec.md](../spec.md)

## Figure rules (server parity)

- [x] CHK001 Is the counting rule for a wallet report specified for every transaction kind and both transfer sides? [Completeness, Spec §FR-003]
- [x] CHK002 Is the cross-wallet transfer rule specified for covered↔covered and covered↔uncovered transfers, including a deleted counter wallet? [Completeness, Spec §FR-004, Edge Cases]
- [x] CHK003 Are balance start/end defined including the opening balance and future-dated transactions? [Clarity, Spec §FR-005, Edge Cases]
- [x] CHK004 Is the balance identity stated as a requirement with a test? [Measurability, Spec §FR-005, §4]
- [x] CHK005 Are grouping key, zero-item exclusion, sort order and tie-break specified exactly as the server's? [Consistency, Spec §FR-006, research R-04]
- [x] CHK006 Is the share rounding rule (half up, "<1%", zero total) specified with integer arithmetic? [Clarity, Spec §FR-007]
- [x] CHK007 Is the waiting-change overlay specified for edits, deletions, creates without a row, refused/clashing changes and phone-only confirmations? [Completeness, Spec §FR-001]
- [x] CHK008 Is "no jump when a waiting change is accepted" stated and testable? [Measurability, Spec §FR-002, SC-003]
- [x] CHK009 Is the planned rule specified for status, waiting confirm/skip, currency, covered transfers and zeros-vs-absent? [Completeness, Spec §FR-016, research R-05]
- [x] CHK010 Is the parity verification method defined with a concrete data set and pass condition? [Measurability, SC-002, research R-13, quickstart]

## Periods and ranges

- [x] CHK011 Is derivation of Budget Periods specified for no "applies from" date, dates after it, and dates before it? [Coverage, Spec §FR-011]
- [x] CHK012 Is the behaviour for an underivable period offline specified (no guess)? [Clarity, Spec §FR-011, US5-S4]
- [x] CHK013 Are the period lookup's request members and the parts of the answer used specified? [Clarity, Spec §FR-019, contracts/api-client.md A1]
- [x] CHK014 Are custom-range limits (order, 366 inclusive, 1900–2999) specified with the refusal copy? [Completeness, Spec §FR-009, Copy]
- [x] CHK015 Is the forward limit of "Next" defined? [Clarity, Spec §FR-008]
- [x] CHK016 Is the trend's anchor for a custom range defined (period containing the last day)? [Clarity, Spec §FR-010]
- [x] CHK017 Is day rollover behaviour (today, period, limit, "known up to") specified? [Coverage, US5-S5, Edge Cases]

## Currencies and scope

- [x] CHK018 Is the default currency defined including the no-profile case? [Clarity, Spec §FR-013]
- [x] CHK019 Is the excluded-wallets notice's content and order specified? [Clarity, Spec §FR-014]
- [x] CHK020 Is the case "currency with no wallet" specified? [Coverage, US6-S3]
- [x] CHK021 Is the membership basis (wallets on the phone now, archived included, any role) consistent with the backend's "now" rule? [Consistency, Spec §FR-004, Edge Cases]

## Display, accessibility, localisation

- [x] CHK022 Are label localisation rules specified for platform, own, partner and unresolvable categories and for period labels? [Completeness, Spec §FR-015]
- [x] CHK023 Are chart text alternatives, RTL mirroring and 200% font requirements specified? [Coverage, Spec §FR-018, SC-006]
- [x] CHK024 Is "state never by colour alone" addressed for income vs expenses and scheduled bars? [Clarity, Spec §FR-018, research R-10]
- [x] CHK025 Are liability and negative amount renderings specified? [Clarity, Spec §FR-017]
- [x] CHK026 Is the overflow case given a defined display instead of a wrong number? [Edge Case, Spec Edge Cases, research R-04]
- [x] CHK027 Are empty states defined (no wallet, no spending, no transaction in range)? [Coverage, US1-S6, FR-023]

## Freshness, server and privacy

- [x] CHK028 Is the refresh trigger (open, floor, pull forced) and the "Updated" time source defined? [Clarity, Spec §FR-020]
- [x] CHK029 Is every handled error code of each consumed endpoint listed with its client behaviour? [Completeness, Spec §5, contracts/api-client.md]
- [x] CHK030 Is it explicit that release builds never call figure endpoints and never show server figures? [Clarity, Spec §FR-001, FR-028]
- [x] CHK031 Are logging limits stated for figures, names, the probe and the lookup? [Completeness, Spec §FR-025, research R-15]
- [x] CHK032 Is sign-out behaviour defined for in-memory state? [Coverage, Spec §FR-029, data-model §4]
- [x] CHK033 Is "no entitlement gating" explicit and consistent with the Plan screen line? [Consistency, Spec §FR-024, §6]

## Architecture and testing

- [x] CHK034 Are all cross-feature reads routed through domain hooks with named contracts? [Consistency, contracts/domain-apis.md, P-10.4]
- [x] CHK035 Is "no schema change" stated with a test that would fail (schema export, graph)? [Measurability, Spec §3, §4]
- [x] CHK036 Is the performance target quantified with a data size and a test? [Measurability, SC-005]
- [x] CHK037 Are E2E flows listed per story and marked deferred per the owner's policy? [Completeness, contracts/ui-and-e2e.md]
- [x] CHK038 Are the backend contract gaps recorded with their effect and a QA check? [Traceability, Spec §9, quickstart step 4]

## Notes

Gaps found and fixed while running this checklist:

- CHK021: the spec said "wallets on the phone" without saying a wallet waiting to be created on the
  phone (not yet on the server) is covered too — now an edge case below the membership bullet: a
  phone-only wallet counts on the phone at once and its figures join the server's after it syncs.
- CHK026: overflow display was only in the edge cases; research R-04 now names the `TooLarge`
  marker and the copy key `reports_too_large` exists.
- CHK013: the lookup's `months` for more than 12 unknown periods needed a rule — research R-07 now
  splits into several reads.
- CHK029: the 406 answer was not listed for the lookup — it is handled by the core like every read
  (no client branch needed) and is now named in contracts/api-client.md.
