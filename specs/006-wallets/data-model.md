# Data Model: Wallets & Membership (client)

Wire shapes are `wallets.yaml` (backend spec 010, `310aa24`). Money is `Long` minor units (P-03);
instants are UTC strings in Room and `Instant` in domain (P-05).

## 1. Tables (Room, schema v3, `feature.wallets.data.local`)

### `wallet` (new)

| Column | Type | Rules |
|---|---|---|
| `id` | TEXT PK | UUIDv7; client-made for new wallets |
| `account_id` | TEXT | signed-in user id that holds this copy (FR-018 of spec 004: never shown under another account) |
| `type` | TEXT | `cash` · `checking` · `savings` · `credit_card` · `loan` · `investment`; unknown → row kept, shown with a generic icon and the "Other" label, never sent changed |
| `name` | TEXT | 1–100 chars |
| `currency` | TEXT | ISO 4217 alpha-3 |
| `opening_balance_minor` | INTEGER | signed |
| `balance_minor` | INTEGER | server cache; placeholder = opening |
| `icon` | TEXT NULL | stored as given |
| `color` | TEXT NULL | stored as given |
| `archived_at` | TEXT NULL | UTC instant |
| `currency_locked` | INTEGER (bool) | server-reported |
| `my_role` | TEXT | `owner` · `editor` · `viewer` |
| `version` | INTEGER | `0` = placeholder, never synced |
| `change_seq` | INTEGER | stored, not interpreted (wave 2) |
| `created_at` | TEXT | UTC instant (placeholder: local time of creation) |
| `updated_at` | TEXT | UTC instant |
| `refreshed_at` | TEXT | when this copy was last written from the server (or created) |

Index: `(account_id, archived_at, created_at)`.

### `wallet_member` (new)

| Column | Type | Rules |
|---|---|---|
| `id` | TEXT PK | membership id |
| `wallet_id` | TEXT | FK-like (no SQL FK: rows are replaced as a set), indexed |
| `member_user_id` | TEXT | |
| `display_name` | TEXT NULL | max 100 |
| `role` | TEXT | `owner` · `editor` · `viewer` |
| `version` | INTEGER | |
| `created_at` | TEXT | ordering (server lists in creation order) |

Rows for a wallet are replaced as a whole by `refreshMembers`; deleted with the wallet.

### `sync_scope` (existing)

Key `wallets` holds the last complete list refresh (`refreshed_at`, shown as "Updated …" offline);
key `wallet:<id>` gets `last_pushed_at` from the engine on each successful send (spec 004).

### `pending_change` (existing, spec 004)

Record type `wallet`, record id = wallet id, scope `wallet:<id>`, payload `WalletDraft` (§3).
No schema change.

### Migration `MIGRATION_2_3`

`CREATE TABLE wallet (…)`, `CREATE INDEX …`, `CREATE TABLE wallet_member (…)`, `CREATE INDEX
index_wallet_member_wallet_id`. Nothing else is touched. `buildAppDatabase` adds it after
`MIGRATION_1_2`. `DatabaseCleaner` adds `walletDao().deleteAll()` and `walletMemberDao().deleteAll()`.

## 2. Wire DTOs (`feature.wallets.data.dto`)

Decoding ignores unknown members (P-02.3); request bodies carry only schema members (P-02.2).

| DTO | Members |
|---|---|
| `WalletDto` | `id, wallet_id, type, name, currency, opening_balance_minor: Long, balance_minor: Long, icon: String?, color: String?, archived_at: String?, currency_locked: Boolean, my_role, version: Long, change_seq: Long, created_at, updated_at, deleted_at: String?` |
| `WalletWriteRequest` | `type, name, currency, opening_balance_minor, icon (explicit null), color (explicit null), version` — PUT |
| `VersionOnlyRequest` | `version` — archive, DELETE body |
| `OwnershipTransferRequest` | `user_id, version` |
| `WalletMemberDto` | `id, wallet_id, member_user_id, display_name: String?, role, version, created_at, updated_at, deleted_at: String?` |

`icon` and `color` are required-nullable in `WalletWrite`: the encoder MUST write `null`, not omit
them (contract test).

## 3. Domain (`feature.wallets.domain`)

```text
enum WalletType(wire, isLiability)  Cash, Checking, Savings, CreditCard(liability), Loan(liability), Investment
enum WalletRole(wire)               Owner, Editor, Viewer         // canEdit = Owner
enum WalletField(pointer)           Name(/name), Currency(/currency), Type(/type),
                                    OpeningBalance(/opening_balance_minor), Icon(/icon), Color(/color)

Wallet(id, type, name, currency, openingBalanceMinor: Long, balanceMinor: Long, icon: String?, color: String?,
       archivedAt: Instant?, currencyLocked: Boolean, myRole: WalletRole, version: Long, createdAt: Instant)

@Serializable WalletDraft(type, name, currency, openingBalanceMinor, icon, color, archive: Boolean = false)
  Wallet.toDraft(); draft.sameFieldsAs(wallet)

WalletView(
  server: Wallet,                  // the row
  pending: WalletDraft?,           // queue payload, null when nothing waits
  pendingDelete: Boolean,
  syncState: None | Waiting (engine Waiting or Sending) | Clash(theirs: Wallet?, deletedElsewhere: Boolean) | Refused(reason: RefusalReason, fields: Map<WalletField, String>),
  displayedBalanceMinor: Long,     // R-10
) { shown = pending ?: server.toDraft(); hidden = pendingDelete && syncState == Waiting; archived = shown.archive || server.archivedAt != null }

enum RefusalReason  NotVerified, NotEntitled, NotOwner, NoLongerAvailable, Invalid, Other(requestId)
Member(userId, displayName: String?, role: WalletRole, isYou: Boolean)
CurrencyTotal(currency, totalMinor: Long, walletCount: Int)

interface PendingPostings { fun deltaFor(walletId): Flow<Long>; fun hasPending(walletId): Flow<Boolean> }   // C4 binds Zero; C6 rebinds
fun interface WalletDeletionParticipant { suspend fun onWalletDeleted(walletId: String) }                  // C6 registers
```

Use cases / services (domain, called by stores):

| API | Does |
|---|---|
| `WalletsService.observeList(): Flow<WalletList>` | active + archived views (hidden ones dropped), totals (R-11), last refresh time |
| `observeWallet(id): Flow<WalletView?>` / `observeMembers(id)` | detail data |
| `create(draft): CreateResult` | gates (R-14) → new UUIDv7 → placeholder row → `engine.enqueue(base 0)` |
| `save(id, draft, baseVersion)` | verified gate → `engine.enqueue(base = baseVersion)`, the version shown when the editor opened (FR-010; a waiting row keeps its older base, spec 004 FR-003) |
| `archive(id)` | `save(id, shown draft.copy(archive = true), row.version)` |
| `delete(id)` | R-06 |
| `keepMine / useTheirs / acceptDeletion / discard / tryAgain(id)` | engine calls (R-07, R-08) |
| `refresh()` / `refreshMembers(id)` | R-09 |
| `transferOwnership(id, userId)` | R-15, online only |
| `canCreateAnother(): Boolean` | C3 gate (R-14) |

## 4. State transitions of a wallet on the phone

```text
(create) ─► placeholder v0 + queue[Waiting] ──200/201──► server copy vN, queue cleared
                    │ delete before first send ─► placeholder and queue row removed (nothing sent)
server copy ─edit/archive─► queue[Waiting] (overlay shown) ──200──► server copy vN+1
            ─delete──────► queue[Waiting, delete] (wallet hidden) ──200 tombstone──► rows removed (+ participants)
queue[Waiting] ──409 live──► Clash ─keepMine─► Waiting (rebased) │ ─useTheirs─► server copy (edit dropped / wallet kept)
queue[Waiting] ──409 tombstone──► Clash(deletedElsewhere) ─acceptDeletion (useTheirs)─► rows removed
queue[Waiting] ──403/404/422──► Refused ─edit─► Waiting │ ─tryAgain─► Waiting │ ─discard─► server copy (v0: removed)
list refresh: not listed ∧ v>0 ∧ no queue row ─► rows removed
sign-out ─► all wallet rows, members and queue rows removed
```

Archive is one-way: once `archived_at` is set there is no "unarchive" control.

## 5. Screen state (MVI, `feature.wallets.ui`)

```text
WalletsState(active: List<WalletCardUi>, archived: List<WalletCardUi>, totals: List<CurrencyTotal>,
             refreshing: Boolean, updatedAt: Instant?, offline: Boolean, notEntitledNotice: Boolean, loaded: Boolean)
WalletsIntent  = Opened | Refresh | CreateClicked | WalletClicked(id) | DismissNotice | Back
WalletsEffect  = OpenEditor(null) | OpenDetail(id) | RequireVerification | NavigateBack

WalletDetailState(wallet: WalletView?, members: List<Member>, membersUpdatedAt: Instant?, canEdit: Boolean,
                  canTransfer: Boolean, confirm: None|Archive|Delete|Transfer(candidates), busy: Boolean, gone: Boolean)
WalletDetailIntent = Opened(id) | EditClicked | ArchiveClicked | DeleteClicked | TransferClicked |
                     ConfirmArchive | ConfirmDelete | ConfirmTransfer(userId) | DismissConfirm |
                     KeepMine | UseTheirs | AcceptDeletion | TryAgain | Discard | Back
WalletDetailEffect = OpenEditor(id) | NavigateBack | ShowMessage(DetailMessage) | RequireVerification

WalletEditorState(mode: Create|Edit(id), type: WalletType, typeEditable: Boolean, name: String, currency: String,
                  currencyEditable: Boolean, amountText: String, iconKey: String?, accentKey: String?,
                  errors: Map<WalletField, FieldError>, canSave: Boolean, saving: Boolean)
WalletEditorIntent = Opened(id?) | TypeChosen | NameChanged | CurrencyChosen | AmountChanged |
                     IconChosen | AccentChosen | SaveClicked | Back
WalletEditorEffect = Saved(id) | NavigateBack | RequireVerification | NotEntitled
```

Each screen has `…Change` and a pure top-level `reduce` (P-16.4).
