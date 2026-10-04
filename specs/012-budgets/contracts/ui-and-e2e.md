# Contract: Screens, copy and E2E flows

## Screens (P-16: Contract, ViewModel, Route, stateless Screen each)

### Budgets (`BudgetsKey`, `ui/list`)

- Top bar "Budgets" with back and an "Add budget" action (48 dp, content description).
- Period line: "25 Sep – 24 Oct · 22 days left" (profile time zone, app language, Western digits). While a start day change is pending: "From 1 Nov, periods start on day 1". Period unknown: no line.
- "Active" list: one `BudgetProgress` per budget (name = category display name or "All expenses"), under it, as needed: "On pace for <amount>", "Also spent in USD (not counted)", "Includes changes waiting to sync", the sync status line (Waiting to sync / Changed on another device / Deleted on another device / Not saved). Period unknown: the name, "Limit <amount>" and "Progress appears after sync".
- "Archived" section (collapsed title + list), same rows.
- Pull to refresh (forced); offline: "Updated <time>" line as on other lists.
- Empty: one line ("Set a monthly limit for a category or for all your spending.") and "Set a budget".
- Tags: `budgets.add`, `budgets.period`, `budgets.pending`, `budgets.active.<i>`, `budgets.archived.<i>`, `budgets.empty`.

### Budget editor (`BudgetEditorKey(budgetId?)`, `ui/editor`)

- Create: "New budget"; category field (opens the choice sheet: "All expenses" first, then C5 own and defaults; "Already budgeted" rows disabled with caption), currency field (selection sheet), limit (`AmountField`, currency digits), "Monthly, starting on day <n>" line with "Change in Profile" hint; "Save".
- Edit/detail: the budget's progress (as on the list) at the top; category, currency and period read-only with "Can't be changed after the budget is created"; limit editable; "Save" (enabled when changed and valid); "Archive budget" (active only) and "Delete budget", each with a confirmation dialog whose confirm reads "Yes, archive it" / "Yes, delete it".
- Status and choices: clash → "Changed on another device" with "Keep mine" / "Use the other device's"; deleted elsewhere → "Accept the deletion"; refused → reason by code/pointer + "Try again" / "Discard".
- Unverified address: Save shows the verification prompt (as categories).
- Tags: `budgetEditor.category`, `budgetEditor.currency`, `budgetEditor.limit`, `budgetEditor.save`, `budgetEditor.archive`, `budgetEditor.delete`, `budgetEditor.keepMine`, `budgetEditor.useTheirs`, `budgetEditor.accept`, `budgetEditor.tryAgain`, `budgetEditor.discard`.

### Home (changed)

- "Budgets" row (`home.budgets`) → Budgets.
- "Budgets" card (`home.budgetsCard`) when at least one active budget: up to 3 `BudgetProgress` rows (highest percentage first), tap → editor; "See all" (`home.budgetsCard.all`) → Budgets.

## Copy (EN / AR, owner review for Arabic)

| Key idea | English | Arabic |
|---|---|---|
| Screen | Budgets | الميزانيات |
| One | Budget | ميزانية |
| All | All expenses | كل المصروفات |
| Limit | Limit | الحد |
| Period | Monthly, starting on day %d | شهريًا، تبدأ في اليوم %d |
| Pace | On pace for %s | بهذا المعدل ستصل إلى %s |
| Others | Also spent in %s (not counted) | صُرف أيضًا بعملة %s (غير محسوب) |
| Waiting | Includes changes waiting to sync | يشمل تغييرات في انتظار المزامنة |
| Unknown | Progress appears after sync | يظهر التقدم بعد المزامنة |
| Pending day | From %1$s, periods start on day %2$d | من %1$s تبدأ الفترات في اليوم %2$d |
| Duplicate | Already budgeted | له ميزانية بالفعل |
| Unknown type | Shown in full on newer versions of the app | يظهر كاملًا في الإصدارات الأحدث من التطبيق |
| Immutable | Can't be changed after the budget is created | لا يمكن تغييره بعد إنشاء الميزانية |
| Archive confirm | Archived budgets leave Home and can't be made active again. | الميزانيات المؤرشفة تختفي من الرئيسية ولا يمكن إعادة تفعيلها. |
| Delete confirm | Delete this budget? This can't be undone. | حذف هذه الميزانية؟ لا يمكن التراجع عن ذلك. |
| Refused category | This category can't have a budget any more. | لم يعد بالإمكان وضع ميزانية لهذه الفئة. |

No "envelope" or "plan" for a budget (string test). No exclamation marks or alarm words in budget strings.

## Maestro flows (`.maestro/012-budgets/`, deferred: E2E on owner's device)

| Flow | Scenario |
|---|---|
| `US1-create-groceries.yaml` | create Groceries 6,000 EGP; listed with progress; "Waiting to sync" then synced |
| `US1-duplicate-blocked.yaml` | Groceries/EGP shown "Already budgeted"; USD allowed |
| `US2-home-card.yaml` | three budgets; Home card order by percentage; "See all" |
| `US2-over-and-pace.yaml` | seeded expenses above the limit; "over" words and percentage > 100; "On pace for" |
| `US3-offline-moves-then-matches.yaml` (parts a/b) | airplane mode, record expense, budget moves with "Includes changes waiting to sync"; reconnect, sync, figures equal `api.js listBudgetProgress` |
| `US4-limit-archive-delete.yaml` | raise limit, archive (moves to Archived), delete |
| `US4-clash.yaml` | API edits the budget; phone edits offline; "Keep mine" |
| `US5-start-day.yaml` | profile start day 25; period line; pending change line |
| `US6-update-reads-budgets.yaml` | budget created through the API before sign-in appears |

`api.js` gains `createBudget`, `deleteBudget`, `archiveBudget`, `listBudgetProgress`, `setStartDay`.
