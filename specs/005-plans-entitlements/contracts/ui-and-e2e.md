# Contract: Screens, test tags and E2E seeds

## Navigation

Account screen → row "Plan" (`account.plan`) → `PlanKey` → Plan screen. Back returns to Account.

## Test tags (FR-019)

| Element | Tag |
|---|---|
| Account row | `account.plan` |
| Plan top-bar refresh | `plan.refresh` |
| Current-plan section | `plan.current` (its name text: `plan.current.name`) |
| Subscription status line | `plan.current.status` |
| Stale / offline note | `plan.stale` |
| Catalogue card | `plan.card.<index>` (0-based, catalogue order) |
| "Current plan" label | `plan.card.<index>.current` |
| Price | `plan.card.<index>.price` |
| Catalogue retry | `plan.catalogue.retry` |

## Copy (new keys, English and Arabic)

`plan_title` ("Plan"), `account_plan` (row), `plan_current_heading` ("Your plan"),
`plan_status_active_renews` ("Active · Renews on %1$s"), `plan_status_active_ends` ("Active · Ends on %1$s"),
`plan_status_no_end` ("Active · No end date"), `plan_status_grace` ("Payment due · Access until %1$s"),
`plan_catalogue_heading` ("Plans"), `plan_current_label` ("Current plan"),
`plan_period_monthly`, `plan_period_yearly`, `plan_period_lifetime`, `plan_period_every_days` ("Every %1$d days"),
`plan_cap_sharing_off` ("Sharing: not included"), `plan_cap_sharing_members` ("Share wallets with up to %1$d people"),
`plan_cap_sharing_unlimited`, `plan_cap_wallets_unlimited` ("Unlimited wallets"), `plan_cap_wallets_max` ("Up to %1$d wallets"),
`plan_cap_export_on` / `_off`, `plan_cap_reports_on` / `_off`,
`plan_stale` ("Showing your last known plan. It may be out of date."), `plan_catalogue_unavailable`,
`cd_refresh` ("Refresh"), `error_feature_not_entitled`.
Arabic uses the glossary's terms (محفظة for wallet) and Western digits. No copy mentions
InstaPay, checkout, payment links, "token" or prices outside Google Play.

## E2E seeds (`run-e2e.sh`, research R-12)

| `# e2e-seed:` | Account | Effect |
|---|---|---|
| `verified-user` | verified, Free | existing |
| `verified-user+plus` | verified | + Plus · Monthly granted for 30 days before launch |
| `verified-user+plus-lifetime` | verified | + Plus · Monthly granted with no end date |
| `verified-user+plus-grace` | verified | + Plus · Monthly granted, then the subscription put in grace for 3 days (assumed default: no use case lapses one on demand) |
| `# e2e-between: grant-plus` (in a `.part2.yaml` header) | — | runner grants Plus between part 1 and part 2, app not reset; part 2 taps `plan.refresh` (a foreground return may fall inside the 60 s floor) |

Flows live in `.maestro/005-plans-entitlements/`.
