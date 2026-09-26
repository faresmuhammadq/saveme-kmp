# Contract: Design system public surface

Package `com.fares.saveme.designsystem`. Source: design canvas boards *Mobile foundations* and *Mobile components*. This is the API every feature screen consumes; feature code MUST NOT style colours, type or spacing directly (SC-008).

## Theme

| Symbol | Contract |
|---|---|
| `AppTheme(content)` | Provides Material 3 `ColorScheme` + `Typography` + `Shapes` mapped from tokens, plus `LocalMoneyColors`, `LocalSpacing`, `LocalAppTypography`. Picks Latin/Arabic font family from layout direction. Light only. |
| `AppTheme.colors / money / type / spacing / shapes` | Read-only accessors for tokens (see [data-model.md §4](../data-model.md)). |

## Components (composable name → required parameters / states)

| Component | Key parameters | States / variants |
|---|---|---|
| `PrimaryButton` | text, onClick, enabled, loading, modifier | enabled, pressed (#3553B0), disabled (sunken-2), loading (spinner, not clickable); 52 dp, full width |
| `SecondaryButton`, `TextButton`, `DestructiveButton` | same as above | same states |
| `AppIconButton` | icon, contentDescription (required), onClick | 48 dp target |
| `AppFab` | icon, text?, expanded, onClick | 56 dp; extended ↔ collapsed |
| `AppTopAppBar` | title, navigationIcon?, actions | 64 dp; back arrow mirrors in RTL |
| `AppNavigationBar` | items, selected, onSelect | 80 dp, pill indicator 56×30 on primaryContainer |
| `SegmentedControl<T>` | options, selected, onSelect | 48 dp |
| `AppFilterChip` | label, selected, onClick | 36 dp visual, 48 dp touch |
| `SettingsSwitchRow` | title, supportingText?, checked, onCheckedChange | whole row toggles |
| `AppTextField` | value, onValueChange, label, supportingText?, errorText?, enabled, keyboardOptions | 52 dp; error replaces supporting text |
| `PasswordField` | as `AppTextField` + visible, onToggleVisible | masked by default; toggle has contentDescription |
| `AmountDisplay` | amountMinor, currency, style | tabular, per-currency fraction digits |
| `MoneyText` | amountMinor, currency, kind (Income / Expense / Liability / Balance) | colour rules per spec FR-006 |
| `BalanceHero` | label, amountMinor, currency, caption? | radius 22, solid primary, no gradient |
| `WalletCard` | name, type (cash, checking, savings, credit_card, loan, investment), amountMinor, currency | type icon tile |
| `TransactionRow` | title, subtitle, amountMinor, currency, kind, pendingSync | 72 dp; pending dot |
| `DateHeader` | date label, dayTotal | sticky-capable |
| `MemberRow` + `RolePill` | name, avatarInitial, role (Owner/Editor/Viewer), isYou | — |
| `BudgetProgress` | name, spentMinor, limitMinor, currency | positive < 80%, pending 80–100%, over > 100%; percent always written |
| `SyncBanner` | state (Offline(pending n) / Synced) | — |
| `InlineAlert` | tone (Info/Warning/Error), text, action? | — |
| `AppSnackbarHost` | message, actionLabel? (e.g. Undo) | — |
| `AppBottomSheet` | title?, content, onDismiss | top radius 22, drag handle, scrim ink 38% |
| `EmptyState` | title, body, action? | — |
| `Skeleton` | shape, modifier | shimmer-free placeholder blocks |

## Guarantees (tested)

- Every clickable component: ≥ 48×48 dp touch target; icon-only components require `contentDescription`.
- Renders without clipping at fontScale 2.0 and in RTL (screenshot tests, research R-15).
- No component reads app/business state; all inputs are parameters.

## Catalogue

`ComponentCatalogScreen` lists every component above in every state, with language toggle (en/ar) and font-scale toggle (1.0/1.5/2.0). Reachable only when `AppConfig.isDebugTools` (dev flavor / debug build).
