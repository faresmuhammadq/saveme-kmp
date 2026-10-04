# Contract: Screens, test tags, copy and E2E

## Navigation

- Home → "Due" card (`home.due`, shown when `DueSummary` > 0) → `RecurringKey(null)` (all wallets).
- Wallet detail → row "Recurring" (`walletDetail.recurring`) → `RecurringKey(walletId)`.
- Transactions (one wallet) → "Due" section rows (`transactions.due.<index>`) → `OccurrenceKey(id)`; header link "Recurring" (`transactions.recurring`) → `RecurringKey(walletId)`.
- Transaction editor (create, writer) → "Make recurring" (`txEditor.makeRecurring`) → `RuleEditorKey(walletId, null, prefill = RulePrefill(...))`, replacing the editor in the back stack. Match offer (`txEditor.match.<index>`) → `RecurringLinks.confirmFromDraft(occurrenceId, draft)`, then `OccurrenceKey(id)` replaces the editor (showing the waiting confirmation).
- Recurring → "Add recurring" FAB (`recurring.add`) → `RuleEditorKey(walletId, null)`; rule row → `RuleDetailKey(ruleId)`; occurrence row → `OccurrenceKey(id)`.
- Rule detail → Edit (`rule.edit`) → `RuleEditorKey(walletId, ruleId)`; Change from a date (`rule.changeFrom`) → `RuleEditorKey(walletId, ruleId, fromDate = true)`; occurrence row → `OccurrenceKey(id)`.
- Occurrence → "Open transaction" (`occurrence.openTransaction`) → `TransactionEditorKey(transactionId, walletId)`; "Open the rule" → `RuleDetailKey`.
- Transaction editor detail of a produced transaction → "Open the rule" (`txEditor.openRule`) → `RuleDetailKey` (FR-038).
- `RulePrefill` (inside `RuleEditorKey`) redacts amounts and texts in `toString()` (P-09.2).

## Screens (design-system components only, P-07)

| Screen | Content |
|---|---|
| Recurring (wallet or all) | `AppTopAppBar` (wallet name or "Recurring"); `SyncBanner` offline with waiting changes; sections "Due" (missed first, `OccurrenceRow`s), "Upcoming" (`OccurrenceRow`s, next 35 days held), "Rules" (`RuleRow`s grouped Active, Paused, Ended; by wallet in all-wallets mode); empty state "No recurring rules yet." with "Add recurring" for writers; `AppFab` "Add recurring" (writer, active wallet); pull to refresh |
| Rule detail | header: description, cadence text, next date, amount (or "x of total" / gam'eya payout), status pill, "Automatic confirmation" line, author caption; status `InlineAlert` (waiting / changed on another device with choices / deleted elsewhere → Accept the deletion / not saved + reason → Try again, Discard / "Waiting for the change to <rule>" for a held split); preview `PreviewDateList` labelled "Dates appear after sync" (unsynced); occurrences list (due, upcoming, history); actions: Edit, Change from a date, Pause / Resume, End, Delete (`DestructiveButton`), Check for new dates (online note); confirms via `AppAlertDialog` |
| Rule editor | preset `SegmentedControl` Custom / Gam'eya / Installment (create only); kind `SegmentedControl` (allowed kinds per preset; read-only on edit); `AmountField`; amount basis (installment: "Each installment" / "Total"); category (`CategoryChoiceSheet`) or "To wallet" `AppSelectField`; gam'eya: number of rounds, payout round, payout category; how often `AppSelectField`, every how many, day of month, start `DateField`, end (Never / On a date / After N times); note, details, person; "Automatic confirmation" switch with explanation; preview `PreviewDateList` (next five, or every round for presets with payout); frozen fields disabled with a one-line reason; "from date" mode: "Change from" `DateField` first, with the note "Rounds before this date stay as they are"; `PrimaryButton` Save |
| Occurrence | title (rule description), date (+ "scheduled for" when edited), amount, kind, category or destination, state with label + icon (Due, Missed, Upcoming, Skipped, Confirmed, Payout), automatic-confirmation refusal `InlineAlert`, "Already confirmed" notice, status `InlineAlert` with choices; actions: Confirm (opens the confirm form in place: date, amount, category, note, details, person, Save "Confirm"); while a confirmation waits: Change it (same form) / Cancel confirmation; Skip / Undo skip, Edit this one (same form, Save "Save"), Open transaction (confirmed), Open the rule |

## Design-system additions (catalogue `RecurringCatalog` + screenshots EN LTR, AR RTL, font 2.0)

- `OccurrenceRow(date, title, amount, kind, state, isPayout, status)` — state shown as icon + text, never colour alone; amounts via `MoneyText`.
- `RuleRow(title, cadence, nextDate, amount, statePill, status)`.
- `DueCard(count, firstTitle, firstDate)` — Home card with chevron, 48 dp target.
- `PreviewDateList(rounds, label)` — compact dated amounts, payout tagged.

## Test tags

| Element | Tag |
|---|---|
| Home / wallet detail / transactions | `home.due`, `walletDetail.recurring`, `transactions.recurring`, `transactions.due.<index>` |
| Recurring | `recurring.list`, `recurring.add`, `recurring.due.<index>`, `recurring.upcoming.<index>`, `recurring.rule.<index>`, `recurring.empty` |
| Rule detail | `rule.status`, `rule.edit`, `rule.changeFrom`, `rule.pause`, `rule.resume`, `rule.end`, `rule.delete`, `rule.catchUp`, `rule.preview`, `rule.occurrence.<index>`, `rule.keepMine`, `rule.useTheirs`, `rule.acceptDeletion`, `rule.tryAgain`, `rule.discard` |
| Rule editor | `ruleEditor.preset.<custom|gameya|installment>`, `ruleEditor.kind.<…>`, `ruleEditor.amount`, `ruleEditor.basis.<per|total>`, `ruleEditor.category`, `ruleEditor.destination`, `ruleEditor.rounds`, `ruleEditor.payoutRound`, `ruleEditor.payoutCategory`, `ruleEditor.frequency`, `ruleEditor.interval`, `ruleEditor.day`, `ruleEditor.start`, `ruleEditor.end.<never|date|count>`, `ruleEditor.endDate`, `ruleEditor.count`, `ruleEditor.note`, `ruleEditor.details`, `ruleEditor.person`, `ruleEditor.autoConfirm`, `ruleEditor.from`, `ruleEditor.preview`, `ruleEditor.save` |
| Occurrence | `occurrence.state`, `occurrence.confirm`, `occurrence.skip`, `occurrence.unskip`, `occurrence.edit`, `occurrence.form.date`, `occurrence.form.amount`, `occurrence.form.category`, `occurrence.form.note`, `occurrence.form.save`, `occurrence.openTransaction`, `occurrence.openRule`, `occurrence.notice`, `occurrence.refusal`, `occurrence.cancelConfirm`, `occurrence.keepMine`, `occurrence.useTheirs`, `occurrence.acceptDeletion`, `occurrence.tryAgain`, `occurrence.discard` |
| Transaction editor | `txEditor.makeRecurring`, `txEditor.match.<index>`, `txEditor.openRule` |

Dialog buttons are tapped by text (spec 006 lesson).

## Copy

Block `<!-- Recurring (spec 010) -->` at the end of both `strings.xml` (about 140 keys, prefix
`recurring_`), glossary words of spec §2; plurals for cadence ("every %d months"); refusal copy in
`RecurringErrorCopy` by code + pointer; automatic-confirmation reasons. **Owner review**: "دفعة",
"القبض", "مستحقة", "فائتة", "تأكيد تلقائي".

## E2E (Maestro, `.maestro/010-recurring/`, every flow starts `# deferred: E2E on owner's device`)

| Flow | Scenario |
|---|---|
| `US1-checkpoint-monthly-upcoming` | create monthly rule from wallet → after sync its next occurrence is listed under Upcoming (brief checkpoint 1) |
| `US1-make-recurring-from-editor` | transaction editor → Make recurring → save → no transaction recorded |
| `US1-offline-preview` | offline create on day 31 → preview shows month-end dates, "Dates appear after sync" |
| `US2-confirm-offline` | seed rule via API dated today → confirm offline with another amount → list shows it waiting, balance moved → online → one transaction (API check) |
| `US2-checkpoint-skip` | skip the next occurrence → leaves Upcoming (brief checkpoint 2); undo skip |
| `US2-auto-confirm-race` | API `materialise` auto-confirms while phone confirms offline → "Already confirmed", one transaction |
| `US3-checkpoint-change-from-date` | confirm round 1, change from round 3's date to a new amount → rounds 1–2 unchanged, later new (brief checkpoint 3) |
| `US3-edit-one` / `US3-pause-end-delete` | edit one occurrence; pause, end, delete with transactions kept |
| `US4-gameya` / `US4-installment-total` | preset previews and created occurrences (payout sum, remainder split) |
| `US5-match-offer` | hand entry resembling a due occurrence → "Confirm … instead" → one linked transaction |
| `US6-catch-up` | Check for new dates online / offline message |

`api.js` gains `createRule`, `materialise`, `confirmOccurrence`, `listOccurrences` actions.
