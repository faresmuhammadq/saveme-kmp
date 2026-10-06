# Contract: Screens, copy and E2E flows

## Screens (P-16: Contract, ViewModel, Route, stateless Screen each)

### Reports (`ReportsKey(walletId: String? = null)`, `ui/reports`)

State: `scope`, `title` (wallet name or "All wallets"), `currency`, `currencies`, `range` (dates,
kind, `canGoNext`), `excluded` (wallet names + codes, other currencies), `freshness`
(`Fresh | Offline(updatedAt) | Failed(updatedAt)`), `includesWaiting`, `summary` (seven lines, liability
flag), `planned?`, `topExpenses` (≤ 5 rows: label display, icon, colour, amount, share, planned),
`moreCategories: Boolean`, `trendLength`, `trend` (`Points | Unknown`), `includeScheduled`,
`knownUntil?`, `customRangeOpen`, `customProblem?`, `refreshing`, `tooLarge`, `state` (`Loading | NoWallets | Ready | WalletGone`).

Intents: `Opened`, `PullToRefresh`, `PreviousClicked`, `NextClicked`, `CustomRangeClicked`,
`CustomRangePicked(from, to)`, `CustomRangeDismissed`, `CurrentPeriodClicked`, `CurrencyPicked(code)`,
`SeeCurrencyClicked(code)`, `TrendLengthPicked(n)`, `IncludeScheduledToggled(on)`, `SeeAllClicked`,
`CategoryClicked(kind)`, `AddWalletClicked`, `BackClicked`.
Effects: `OpenCategories(args)`, `OpenWallets`, `Close`.

Test tags: `reports.screen`, `reports.currency`, `reports.range.previous`, `reports.range.next`,
`reports.range.label`, `reports.range.custom`, `reports.range.current`, `reports.excluded`,
`reports.excluded.see.<CODE>`, `reports.freshness`, `reports.waiting`, `reports.summary.income`,
`reports.summary.expenses`, `reports.summary.net`, `reports.summary.transfers_in`,
`reports.summary.transfers_out`, `reports.summary.balance_start`, `reports.summary.balance_end`,
`reports.planned`, `reports.category.<index>`, `reports.categories.see_all`, `reports.trend.length`,
`reports.trend.chart`, `reports.trend.row.<index>`, `reports.trend.unknown`, `reports.scheduled.switch`,
`reports.empty.add_wallet`.

### Spending by category (`ReportCategoriesKey(walletId?, from, to, currency, includeScheduled, kind)`, `ui/categories`)

State: `title`, `range`, `currency`, `kind` (`Expenses | Income`), `total`, `planned?`, `rows`,
`includesWaiting`, `state`. Intents: `Opened`, `KindPicked`, `BackClicked`. Effects: `Close`.
Test tags: `report_categories.kind`, `report_categories.row.<index>`, `report_categories.total`.

### Home (changed): row "Reports" (`home.reports`) after "Budgets" → `ReportsKey()`.

### Wallet detail (changed): action "Report" (`wallet.report`) → `ReportsKey(walletId)`, every role.

## Design-system components (catalogue section "Reports"; goldens EN LTR, AR RTL, 2.0)

`ShareBarRow`, `TrendBarChart`, `TrendTable`, `ReportFigureRow`, `SummaryCard`,
`ExcludedWalletsNotice` (research R-10).

## Copy (EN / AR; Arabic for owner review)

| Key | English | Arabic |
|---|---|---|
| reports_title | Reports | التقارير |
| reports_home_row | Reports | التقارير |
| reports_wallet_action | Report | التقرير |
| reports_all_wallets | All wallets | كل المحافظ |
| reports_currency | Currency | العملة |
| reports_previous | Previous period | الفترة السابقة |
| reports_next | Next period | الفترة التالية |
| reports_current | Current period | الفترة الحالية |
| reports_custom | Custom range | فترة مخصصة |
| reports_custom_problem | Pick up to 366 days, start before end | اختر حتى 366 يومًا، والبداية قبل النهاية |
| reports_income | Income | الدخل |
| reports_expenses | Expenses | المصروفات |
| reports_net | Net | الصافي |
| reports_transfers_in | Transfers in | تحويلات واردة |
| reports_transfers_out | Transfers out | تحويلات صادرة |
| reports_balance_start | Balance at start | الرصيد في البداية |
| reports_balance_end | Balance at end | الرصيد في النهاية |
| reports_owed | Owed %1$s | مستحق %1$s |
| reports_by_category | Spending by category | المصروفات حسب الفئة |
| reports_see_all | See all | عرض الكل |
| reports_no_spending | No spending in this period | لا مصروفات في هذه الفترة |
| reports_share_under_one | <1% | أقل من 1% |
| reports_trend | Trend | الاتجاه |
| reports_trend_periods | %1$d periods | %1$d فترات |
| reports_trend_summary | Income and expenses for %1$d budget periods; the table below lists each one | الدخل والمصروفات لـ %1$d فترات ميزانية؛ الجدول أدناه يعرض كل فترة |
| reports_period_column | Period | الفترة |
| reports_include_scheduled | Include scheduled | تضمين المجدول |
| reports_scheduled_amount | Scheduled: %1$s | مجدول: %1$s |
| reports_scheduled_known_until | Scheduled money is known up to %1$s | المبالغ المجدولة معروفة حتى %1$s |
| reports_not_included | Not included: %1$s | غير مشمولة: %1$s |
| reports_see_currency | See %1$s report | عرض تقرير %1$s |
| reports_waiting | Includes changes waiting to sync | يتضمن تغييرات بانتظار المزامنة |
| reports_offline_updated | You're offline · Updated %1$s | أنت غير متصل · آخر تحديث %1$s |
| reports_failed_updated | Couldn't refresh · Updated %1$s | تعذر التحديث · آخر تحديث %1$s |
| reports_online_only | Shown when you're online | يظهر عند الاتصال بالإنترنت |
| reports_too_large | Too large to show | أكبر من أن يُعرض |
| reports_no_wallets | Add a wallet to see reports | أضف محفظة لعرض التقارير |
| reports_add_wallet | Add a wallet | أضف محفظة |
| report_categories_title | By category | حسب الفئة |
| reports_scheduled_legend | Scheduled | مجدول |
| report_categories_expenses | Expenses | المصروفات |
| report_categories_income | Income | الدخل |

`reports_category_unavailable` is not added: the existing C5 string `category_unavailable` is reused.

## Maestro flows (`.maestro/014-reports/`, each starting `# deferred: E2E on owner's device`)

| Flow | Covers |
|---|---|
| `US1-summary-and-shares.yaml` | seeded EGP data → summary values, top categories and shares (brief checkpoint) |
| `US1-see-all.yaml` | Spending by category, Expenses/Income |
| `US2-offline-waiting.yaml` | offline expense → counted at once, waiting note, "Updated" caption |
| `US2-after-sync-equal.yaml` | after sync the note goes; values equal seeded server values |
| `US3-wallet-report.yaml` | wallet detail → Report; shared wallet counts the partner's expense (`+partner` seed) |
| `US3-liability.yaml` | credit card balances as "Owed" |
| `US4-trend.yaml` | 3/6/12 choice, table rows |
| `US5-previous-next-custom.yaml` | period steps, custom range accepted, 367 days refused |
| `US6-currencies.yaml` | USD wallet excluded notice, "See USD report" |
| `US7-scheduled.yaml` | rent rule scheduled → "Scheduled: …", balances unchanged |
| `common/open-reports.yaml` | Home → Reports |
| `common/seed-reports.yaml` | API seed: wallets, transactions, transfer, rule (`api.js` additions) |
