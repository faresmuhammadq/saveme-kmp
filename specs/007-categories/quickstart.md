# Quickstart: Categories (client)

## Prerequisites

- Branch `007-categories` from `main` `f64e0f6` (C14 engine, C3 entitlements, C4 wallets; Room v3).
- saveme-backend dev container with spec 011 (`e358537` or later) migrated **and seeded** (`php artisan db:seed`, else `/platform-categories` is empty); spec 012 (transactions) for the in-use checks; `devApiHost` set (README).
- For E2E: one device in `adb devices`, English locale; Maestro installed. **Deferred to the owner's device** (owner decision).

## Automated checks

```bash
./gradlew :shared:testAndroidHostTest              # unit, contract, Room DAO + migration 3→4, AppGraphTest, screenshots verify
./gradlew :shared:verifyRoborazziAndroidHostTest
./gradlew :shared:compileKotlinIosSimulatorArm64   # iOS still compiles (P-10)
.specify/scripts/bash/run-e2e.sh 007-categories    # deferred: owner's device
```

Expected: all green; `shared/schemas/…AppDatabase/4.json` exists and is committed with the change.

## Manual checks (dev build, owner's device)

1. **Defaults** — Home → Categories: Expense lists 19 defaults starting "Groceries", Income lists 7 starting "Salary". Account → language Arabic: the same rows read "بقالة" and "مرتب" at once; airplane mode on, switch back to English: names switch without a connection.
2. **Create offline** — airplane mode; New category → "Coffee", icon restaurant, teal → Save: listed under "Your categories" with "Waiting to sync". Network back: the mark clears within a minute; `GET /categories` lists it with `icon: "restaurant"`, `color: "#1F7F8C"`.
3. **Kind** — open "Coffee": it shows "Expense", which can't be changed.
4. **Clash** — open "Coffee"; from another session `PUT /categories/{id}` name "Café" with the current version; on the phone rename to "Coffee beans" and save: "Changed on another device." with both names; "Keep mine" wins on the server.
5. **Archive** — archive "Coffee": it moves to "Archived"; the confirmation says it can't be undone.
6. **In use** — create "Gym"; from another session record an expense with it (`PUT /transactions/{id}`); pull to refresh; open "Gym": no Delete, the in-use note shows.
7. **In-use refusal** — create "Books" and sync; airplane mode; delete "Books"; from another session record an expense with it; network back: "Not deleted: this category is in use." with "Archive instead" and "Keep the category"; "Archive instead" moves it to Archived and the server shows `archived_at`.
8. **Delete never sent** — airplane mode; create "Typo", delete it; network back: nothing about "Typo" reaches the server.
9. **Sign-out** — sign out with nothing waiting and sign in as another person: none of the first person's categories show; defaults load again.
10. **Scale** — seed 200 categories through the API; open Categories offline: the list shows within a second and scrolls smoothly (SC-005).
11. **Accessibility** — TalkBack on the list and editor: rows read name, "Default"/"Archived" and sync marks; icon and colour choices are announced; 200% font: nothing clipped.

Record deviations in plan.md "Implementation notes".
