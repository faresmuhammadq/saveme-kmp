# Contract: Screens and copy

| Place | Shows | Tags |
|---|---|---|
| Account screen | "N changes waiting to sync" / "M need your decision" / "Last synced %1$s", and "Sync now" when N > 0 | `account.syncStatus`, `account.syncNow` |
| Sign-out sheet (Home and Account) | When waiting > 0: "%1$d changes haven't synced. Signing out now discards them." plus "Sync now" (online) above Sign out | `signOut.pendingWarning`, `signOut.syncNow` |
| Profile screen | Unchanged from spec 002 ("Saved on this phone. Waiting to sync.", conflict card, rejected fields) | unchanged |
| Tombstone clash (future record screens) | "Deleted on another device." with "Restore with my change" / "Accept the deletion" | defined per screen when a deletable record type lands (C4+) |

Copy keys (English and Arabic), plural where counted: `sync_waiting_count`, `sync_needs_decision_count`,
`sync_last_synced`, `sync_never`, `action_sync_now`, `sign_out_pending_warning`,
`sync_deleted_elsewhere`, `action_restore_with_mine`, `action_accept_deletion`. The last three are
added now so C4 can use them; no screen shows them yet.
