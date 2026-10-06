# Research: Push Notifications (client)

Phase 0 for [plan.md](plan.md). Each decision resolves a choice the spec left to planning; all
follow the code at `main` `e0eef36` and the backend contract at `saveme-backend@e8c308a` plus the
uncommitted `notifications.yaml` v1.14 (backend spec 023). Items marked *assumed default* were
chosen under the owner's delegated authority as the most conservative option.

## R-01 Where the feature lives

- **Decision**: a new bounded context `feature.notifications` (backend context "Notifications"), with `data/`, `domain/`, `ui/`, `di/`:
  - `data/`: `NotificationsApi` (four calls), DTOs, `PushRegistrationStore` and `ShownNotificationsStore` (key-value records). Takes and returns plain values; never imports `domain` or `ui` (P-10.6).
  - `domain/`: the seam ports (`PushTokenProvider`, `NotificationDisplay`, `NotificationPermissionState`, `PushRegistrationScheduler`), `PushMessage` + `PushPayloadParser`, `PushRoute` + `PushRouter`, `PushRegistrationService`, `PushCoordinator`, `PushReceiver`, `NotificationPreferencesService`, `NotificationOpenService`, `NotificationPromptGate`, `PushSignOutParticipant`, `PushDataCleaner`.
  - `ui/`: `settings/` (Notifications screen), `open/` ("Opening…" screen), `invitation/` (invitation notice), `prompt/` (explanation sheet), `NotificationsErrorCopy.kt`.
  - Android adapters in `shared/src/androidMain/kotlin/com/fares/saveme/feature/notifications/` (as `AndroidLinkSharer` in spec 013): `AndroidNotificationDisplay` (channels + posting + cancel-all), `AndroidNotificationPermissionState`, `WorkManagerPushRegistrationScheduler` + `PushRegistrationWorker`, `rememberNotificationPermissionRequester` actual, `openAppNotificationSettings`.
  - iOS actuals: no-ops (P-15).
  - The Firebase adapter (`FcmPushTokenProvider`, `AppFirebaseMessagingService`) lives only in `:androidApp`, in the gated phase, so `:shared` never sees Firebase.
- **Rationale**: one context per backend context (P-01); `core` stays feature-free (P-10.7); the gated dependency touches one module.
- **Alternatives**: inside `feature.account` (rejected: account owns profile and sessions; preferences, display and routing are a separate concern and would bloat a hotspot); the seam in `core.push` (rejected: nothing in `core` needs it, and the Android platform module already binds feature-domain ports, e.g. `LinkSharer`).

## R-02 The Firebase seam

- **Decision**: two shared-code surfaces, nothing else knows the push service:
  ```kotlin
  interface PushTokenProvider {
      val isAvailable: Boolean                 // false: no provider in this build
      suspend fun currentToken(): String?      // null until the service issues one
      suspend fun discard()                    // forget it on the phone (sign-out)
  }                                            // rotate() exists only on DevFakePushTokenProvider (R-15)
  class PushReceiver(…) {                      // called by the platform service
      fun onNewToken()                         // → PushCoordinator.onTokenChanged()
      suspend fun onMessage(data: Map<String, String>, title: String?, body: String?)
  }
  ```
  Bindings: `UnavailablePushTokenProvider` (staging, prod, iOS — until the gated phase), `DevFakePushTokenProvider` (dev builds: `isDebugTools`), `FcmPushTokenProvider` (gated phase, `:androidApp`, all flavors; dev keeps the simulate tool). The token never crosses into `data` storage; `PushRegistrationService` reads it, sends it, and keeps only its fingerprint (R-04).
- **Gated phase content**: `FcmPushTokenProvider` (`FirebaseMessaging.getInstance().token.await()` via a small `suspendCancellableCoroutine` over the `Task`, `deleteToken()` for `discard`), `AppFirebaseMessagingService : FirebaseMessagingService` (`onNewToken` → `PushReceiver.onNewToken()`; `onMessageReceived` → `PushReceiver.onMessage(message.data, message.notification?.title, message.notification?.body)`), manifest `<service android:exported="false">` with the `com.google.firebase.MESSAGING_EVENT` filter, and meta-data `com.google.firebase.messaging.default_notification_icon` (the same monochrome icon the app posts with). No `default_notification_channel_id`: the server always names a channel, and none of the six is a neutral default, so a message without one goes to the system's fallback channel (*assumed default*). `firebase_messaging_auto_init_enabled` stays default (true) so a token exists at first sign-in; `firebase_analytics_collection_deactivated` = true and no Analytics artifact.
- **Rationale**: FR-045/FR-046; the backend made the same split (`PushGateway` port, backend R-1).
- **Alternatives**: `expect class` for the provider (rejected: three bindings per platform, an interface is simpler to fake); putting the Firebase service in `:shared/androidMain` (rejected: the dependency would enter the shared library and every host test classpath, against SC-009).

## R-03 Payload parsing, collapse keys and routing (FR-023, FR-030, FR-031)

- **Decision**: pure common code.
  - `PushPayloadParser.parse(data: Map<String, String>): PushMessage` returns a sealed `PushMessage`: one case per contract type carrying typed ids (`OccurrenceDue(walletId, occurrenceId)`, `OccurrenceAutoConfirmRefused(…)`, `OccurrencesDueSummary`, `BudgetExceeded(budgetId)`, `InvitationReceived(invitationId?)`, `MemberRoleChanged(walletId, role?)`, `MemberRemoved(walletId?)`, `WalletOwnershipReceived(walletId)`, `SubscriptionEnding`, `SubscriptionLapsed`, `ExportReady(exportId)`, `DeletionReminder`), plus `Malformed(type)` (known type, a needed id missing or not a UUID) and `Unknown` (unknown type, schema ≠ "1", missing `type`). Each case carries `notificationId: String?` and `category`. Unknown keys are ignored (P-02.3). `toString` prints the type only (P-09.2).
  - `PushMessage.collapseKey` from the contract table (`occurrence:{id}`, `occurrences_due`, `budget:{id}`, `invitation:{id}`, `wallet:{id}`, `subscription`, `export:{id}`, `deletion`); `Unknown`/`Malformed` → `"other:{notification_id}"` (never collapses).
  - `PushRouter.route(message): PushRoute` — `PushRoute(read: PushRead, target: PushTarget)`, `PushRead ∈ {None, User, Wallet(id), Everything, Entitlements, Account}`, `PushTarget ∈ {Occurrence(id), RecurringAll, Budget(id), Budgets, InvitationNotice, Wallet(id), Wallets, Plan, Export(id), Exports, AccountDeletion, Home}`, exactly the spec FR-031 table. The navigation layer maps `PushTarget` to `NavKey`s (`OccurrenceKey(id, confirm = false)`, `RecurringKey(null)`, `BudgetEditorKey(id)`, `BudgetsKey`, `InvitationNoticeKey`, `WalletDetailKey(id)`, `WalletsKey`, `PlanKey`, `ExportDetailKey(id)` / `ExportsKey` (spec 016) — `AccountKey` until 016 is merged, `DeleteAccountKey`, `HomeKey`).
- **Rationale**: one table, three tests (parse, collapse, route) cover all 12 types; P-10.3 (domain has no Compose/nav types; the nav mapping lives in `navigation`).
- **Alternatives**: routing on `category` (rejected: the category does not identify the screen); URLs in the data (not in the contract; the server sends none).

## R-04 Registration state and the reconcile job (FR-001–FR-006)

- **Decision**: one idempotent operation, `PushRegistrationService.reconcile(trigger): ReconcileOutcome`, run by the background job; it reads everything at run time:
  1. Signed-in person and stored session (`SecureStore.read()`); none → `Nothing`.
  2. `provider.isAvailable` false → `Nothing`.
  3. `allowed = permissionState.notificationsAllowed()` (Android: `POST_NOTIFICATIONS` granted on 33+ and `NotificationManagerCompat.areNotificationsEnabled()`).
  4. Record `PushRegistrationRecord(userId, sessionId, fingerprint, registeredAt)` from `PushRegistrationStore` (JSON under key `push_registration`).
  5. Not allowed: record for this session → `DELETE` → clear record (204 and 404 both clear) → `Unregistered`; no record → `Nothing`.
  6. Allowed: `token = provider.currentToken()`; null → `Nothing` (the provider will call `onNewToken`). `fp = fingerprint(token)`. If record matches (user, session, fp) and `now − registeredAt < 24 h` and the trigger is `Foreground` → `Nothing`. Otherwise `PUT` → save record → `Registered`.
  7. `PUT` 404 → re-read the stored session; if its id differs from the one sent and this run has not retried → repeat step 6 once with it; else store a `stoppedFor = sessionId` marker so later foreground triggers skip until a sign-in (new session id) or a new token clears it → `Stopped`.
  8. 429 → `RetryAfter(seconds)`; offline, timeout, 5xx → `RetryLater`; 422 → `Stopped` (logged by outcome name; a contract bug); 401 → the app-wide session end handles it → `Nothing`.
  - Fingerprint: FNV-1a 64-bit over the UTF-8 bytes, hex (pure Kotlin; change detection only, never compared across people; not a security control — *assumed default*, no crypto dependency).
  - Triggers: `SignedIn`, `TokenChanged`, `PermissionChanged`, `Foreground`. Only `Foreground` honours the 24-hour skip; the others always register when allowed (FR-002).
- **Rationale**: a single reconcile means no ordering bugs between "register", "unregister because off" and "retry"; reading the session at run time gives FR-003's "the session stored when it runs".
- **Alternatives**: separate register/unregister jobs (rejected: races on permission flips); storing the token in the encrypted store (rejected: FR-004, and the provider already keeps it).

## R-05 Background execution (FR-003)

- **Decision**: `PushRegistrationScheduler.request(trigger)` — Android: `WorkManager` unique work `push-registration`, `ExistingWorkPolicy.APPEND_OR_REPLACE`, `NetworkType.CONNECTED`, expedited when quota allows (as `WorkManagerSyncScheduler`), exponential backoff from 30 s; the trigger travels as input data. `PushRegistrationWorker` (`CoroutineWorker`, `KoinComponent`, as `SyncDrainWorker`) calls `reconcile`; `RetryLater` → `Result.retry()`; `RetryAfter(s)` → enqueue a new run delayed by `s` and `Result.success()`; anything else → `Result.success()`. iOS: `NoopPushRegistrationScheduler`. Host tests use `InlinePushRegistrationScheduler` (runs `reconcile` immediately) and Robolectric `WorkManagerTestInitHelper` for the Android scheduler.
- **Rationale**: survives process death and waits for a connection (FR-003), reuses the existing WorkManager dependency.
- **Alternatives**: a coroutine on the app scope (rejected: lost on process death; `onNewToken` often arrives with the app in the background).

## R-06 Triggers (FR-002)

- **Decision**: `PushCoordinator` (domain), started from `notificationsModule` like `AccountSyncCoordinator`:
  - `start()`: on the transition to signed-in → `request(SignedIn)`.
  - `onForeground()` (added to `App.kt`'s `LifecycleStartEffect` list): signed in → `request(Foreground)` (the job decides; this is also where a permission revoked in system settings is noticed, FR-006).
  - `onTokenChanged()` (from `PushReceiver.onNewToken`) → `request(TokenChanged)`.
  - `onPermissionChanged()` (from the permission requester's result and the Notifications screen's resume) → `request(PermissionChanged)`.
- **Rationale**: matches the existing coordinator pattern; cheap (the job no-ops in under a millisecond when nothing changed).

## R-07 Sign-out (FR-007, FR-043)

- **Decision**:
  - `core.session` gains `fun interface SignOutParticipant { suspend fun beforeSignOut() }` (core event, P-10.7).
  - `SignOutUseCase` gains `participants: () -> List<SignOutParticipant>` (resolved lazily, spec 006 lesson) and a `participantTimeout: Duration? = 2.seconds`; it runs every participant inside one `withTimeoutOrNull(participantTimeout)` (each wrapped so one failure does not skip the others), then the existing server sign-out with its 5-second bound, then `signOutLocally`.
  - `PushSignOutParticipant`: if the registration record belongs to the stored session → `DELETE /me/devices/{id}` once (any answer ignored).
  - `PushDataCleaner : UserDataCleaner` (runs for user sign-out and remote session end alike): `display.cancelAll()`, `PushRegistrationStore.clear()`, `ShownNotificationsStore.clear()`, `scheduler.cancel()`, then `provider.discard()` launched on the app scope with a 10-second bound (never delays the sign-out screen; Firebase's `deleteToken` needs the network and retries on its own).
- **Rationale**: spec clarification 5; the remote-end path (`SessionManager.signOutLocally` on `ApiEvent.SessionEnded`) runs only cleaners, so no `DELETE` with a dead credential.
- **Alternatives**: rely on the server's session-end removal only (rejected: an offline sign-out would leave a live session and push reaching a signed-out phone); a `DELETE` from the cleaner (rejected: the credential is already wiped there).

## R-08 Channels (FR-022)

- **Decision**: `NotificationDisplay.ensureChannels(labels: ChannelLabels)` where `ChannelLabels` holds the six names and descriptions resolved from string resources by common code. Android: platform `NotificationChannel(id, name, IMPORTANCE_DEFAULT)` with `description`, `lockscreenVisibility = Notification.VISIBILITY_PRIVATE`, `setShowBadge(true)`, created with `NotificationManager.createNotificationChannels` (idempotent; a second call updates name and description only, which is how the language change renames them). API < 26: no-op. Called from `App()` in a `LaunchedEffect(effectiveLanguage)` after `AppLocaleProvider` set the locale (the existing override sets `Locale.default`, so `getString` resolves in the app language), and from `PushReceiver` before posting (system language fallback, covers a message arriving before the UI ever ran after a data clear).
- **Rationale**: the server names the channel by category id; a channel that does not exist sends system-displayed messages to FCM's fallback channel, so channels must exist before the first registration — they do, because registration needs a sign-in, which needs the UI.
- **Alternatives**: `NotificationChannelCompat` (rejected: no lock-screen visibility setter); creating channels in `Application.onCreate` (rejected: the app-language override is not applied there).

## R-09 Foreground display, de-duplication, background read (FR-023–FR-028)

- **Decision**: `PushReceiver.onMessage(data, title, body)`:
  1. Nobody signed in → drop (FR-027).
  2. `message = parser.parse(data)`; `title`/`body` blank → drop (FR-025).
  3. `notification_id` in `ShownNotificationsStore` (≤ 100 most recent, per person, JSON list) → drop; else add.
  4. `display.post(PostedNotification(channel = message.category ?: fallback, tag = message.collapseKey, title, body, tap = data filtered to the contract keys))`.
  5. Known type → `requestRead(router.route(message).read)` through `SyncRunner.refresh(target, force = true)` on the app scope (`Entitlements`/`Account` reads are skipped here: they are cheap on tap).
  - Android `post`: `NotificationCompat.Builder(context, channel)` with the small icon passed in by the host (`androidPlatformModule(…, notificationIcon = R.drawable.ic_stat_notification)`), `setContentTitle/Text`, `setAutoCancel(true)`, `setVisibility(VISIBILITY_PRIVATE)`, `setPublicVersion(` builder with "New notification" `)`, `setContentIntent(PendingIntent.getActivity(context, tag.hashCode(), launchIntent.putExtras(tap), FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))` where `launchIntent` = the package's launch intent (`MainActivity`, `FLAG_ACTIVITY_SINGLE_TOP`). `notify(tag, 0, …)`. On API 33+ without permission `notify` is skipped (and `post` returns `false`).
  - `NotificationCompat` comes from `androidx.core`, already on the `:shared` Android classpath transitively (Compose UI and Activity depend on it); if the compiler needs a direct declaration, add `libs.androidx.core.ktx`, which is already in the version catalog — no new artifact enters the build (*assumed default*; recorded in the plan's Constitution Check P-14).
- **Rationale**: identical look and collapse behaviour in foreground and background; the tap extras have the same shape as FCM's background extras, so one tap path (R-10) serves both.
- **Alternatives**: an in-app banner while foreground (rejected: second copy and second language source); trusting `RemoteMessage.notification.tag` (the gated adapter may pass it later, but the derived key works without Firebase).

## R-10 Taps (FR-029–FR-034, FR-044)

- **Decision**:
  - Intake: `MainActivity` (host) forwards `intent.extras` filtered to string values of the contract keys (`schema`, `type`, `category`, `notification_id`, `wallet_id`, `occurrence_id`, `budget_id`, `invitation_id`, `export_id`, `role`) to a new `navigation.PushTapBus` (shape of `DeepLinkBus`: replay 1, `consume()`), from `onCreate` (only when `savedInstanceState == null`) and `onNewIntent`, then removes those extras from the intent so a re-delivery cannot repeat it. A tap is recognised by `type` + `notification_id` both present (background FCM taps also carry `google.message_id`, ignored). The filtering helper (`PushTapExtras.from(bundle)`) lives in `shared/androidMain` so it is Robolectric-tested.
  - Handling in `AppNavHost`: collected like deep links, after the auth transition; signed out → `consume()` and ignore (FR-029). Signed in → `router.route(parser.parse(data))` → if the read is `None` → push the target key; else push `NotificationOpenKey(route)` ("Opening…"), replacing an `NotificationOpenKey` already on top (FR-033).
  - `NotificationOpenViewModel` (MVI) calls `NotificationOpenService.open(route)`: `Wallet`/`User` reads skip the wait when the record is present (`hasRecord` via the target feature's domain lookups, R-11) → `Ready(target)` at once and the read continues on the service scope; otherwise run the read with a 10-second `withTimeoutOrNull` → `Ready(target)` in every case. Effect `Replace(target)` → the nav host swaps the `NotificationOpenKey` for the target key. `Cancel` → pop.
  - Never an action: the service's only collaborators are `SyncRunner.refresh`, `SessionManager.refreshEntitlements`, `AccountRefresher.refresh` and read-only lookups; a test with recording fakes asserts nothing else is called.
- **Rationale**: one path for foreground and background taps; launcher extras are untrusted (FR-044), so only navigation results; P-16 keeps the wait in a store.
- **Alternatives**: a non-exported trampoline activity (rejected: background FCM taps go to the launcher activity anyway, because the server sets no `click_action`); App Link URLs (not in the contract).

## R-11 "Is the record on the phone?" (FR-032)

- **Decision**: `notifications/domain` defines `fun interface RecordPresence { suspend fun has(target: PushTarget): Boolean }`, implemented in `notifications/domain` by delegating to the other features' domain APIs (allowed, P-10.4): wallets (`WalletsService` lookup of one wallet), recurring (`RecurringService` lookup of one occurrence), budgets (`BudgetsService` lookup of one budget). The exact method per service is chosen at implementation; where a service has only a `Flow`, use `first()`; if none exists, add a one-line `suspend fun exists(id): Boolean` to that feature's domain service (additive, tested there).
- **Rationale**: no feature imports another's `data`; no core change.
- **Alternatives**: always wait for the read (rejected: SC-003's 1-second open for known records).

## R-12 Preferences (FR-014–FR-021)

- **Decision**: `NotificationPreferencesService` (domain) with `load(): PreferencesLoad` (`Loaded(prefs)`, `Offline`, `Failed(requestId)`) and `save(prefs, version): PreferencesSave` (`Saved(prefs)`, `Conflict(current)`, `RateLimited(seconds?)`, `Offline`, `Invalid(requestId)`, `Failed(requestId)`); `current` decoded from `ProblemDetails.current` with the same DTO (unreadable → `Failed`). Domain model `NotificationPreferences(version, categories: Map<NotificationCategory, Boolean>, quietHours: QuietHours(enabled, start: QuarterHour, end: QuarterHour), updatedAt: Instant?)`; `QuarterHour(minutes: Int)` 0..1425 step 15, parsed from and printed to `HH:MM`. The store keeps `read` (last read/saved) and `edited`; `dirty = edited != read`; `valid = start != end`. No automatic retry (P-02.6), never queued (spec clarification 3).
  - Profile time zone for the note: from the account domain's profile (`ProfileSyncer`'s observed profile `timezoneId`), shown as the IANA id (e.g. `Africa/Cairo`) — *assumed default*: no localized zone names (no new data).
- **Rationale**: backend R-6 and the api-conventions section; P-04.4 spirit (never silently overwrite).

## R-13 Quarter-hour time field (FR-016)

- **Decision**: new design-system component `TimeOfDayField(label, minutes: Int, onChange: (Int) -> Unit, stepMinutes = 15, enabled, errorText, modifier)` built from the existing `AppSelectField` + `SelectionSheet`/`SelectionList` (96 options for a 15-minute step), each option rendered `HH:MM` with Western digits in an LTR run (`designsystem/format` gains `formatTimeOfDay(minutes)`), the sheet scrolled to the current value. Catalogue entry and screenshot tests (EN, AR, 2.0).
- **Rationale**: P-07.3; the existing Western-digit date components (`DateField`, `AppDatePickerDialog`) have no time picker; Material 3's `TimePicker` is minute-granular and localises digits (the same Arabic-Indic digit problem spec 001 worked around for dates).
- **Alternatives**: Material `TimePicker` with snapping (rejected above); two dropdowns hour/minute (rejected: two controls per time doubles the targets at 200 %).

## R-14 Permission flow (FR-010–FR-012, FR-020)

- **Decision**:
  - Domain port `NotificationPermissionState { fun notificationsAllowed(): Boolean; fun canAskAtRuntime(): Boolean /* API ≥ 33 */ }` (Android: `ContextCompat.checkSelfPermission` + `NotificationManagerCompat.areNotificationsEnabled`).
  - UI seam `@Composable expect fun rememberNotificationPermissionRequester(): NotificationPermissionRequester` with `status(): PermissionStatus { Allowed, Askable, Blocked }` and `request(onResult: (Boolean) -> Unit)`. Android actual: `rememberLauncherForActivityResult(RequestPermission)`; `Askable` = API ≥ 33, not granted, and (`never asked` or `shouldShowRequestPermissionRationale`); `Blocked` otherwise when not allowed. "Asked before" is a key-value flag (`push_permission_asked`), per installation. iOS actual: `Blocked` and a no-op request.
  - `openAppNotificationSettings()` (expect/actual): Android `Settings.ACTION_APP_NOTIFICATION_SETTINGS` with `EXTRA_APP_PACKAGE` (API 26+), app details settings below.
  - `NotificationPromptGate` (domain): `shouldOffer(permissionAskable: Boolean)` = `provider.isAvailable && permissionAskable && !offered`; `markOffered()` (`push_prompt_offered` flag, per installation, not cleared at sign-out — it is about the phone, not the person).
  - The explanation sheet is global (like `EmailNotVerifiedSheet`) in `AppNavHost`, raised from the effects that mark the moments, in the nav host's existing `when`: `BudgetEditorEffect.Saved` while the entry's key is `BudgetEditorKey(budgetId = null)` (a create); `InvitationEffect.OpenWallet` (emitted after a successful join, and also from "Open wallet" when the person is already a member — treated as the same shared-wallet moment, FR-011; no change to the sharing feature); and a new additive `RuleEditorEffect.Created` that the rule editor emits once after a successful create, just before its existing `Close` (today a save and a cancel both end in `Close`, so the moment cannot be told apart without it).
- **Rationale**: Android's rule that the dialog cannot be shown after two refusals needs the rationale flag, which needs an Activity — so status lives in the UI seam; domain only needs "allowed".

## R-15 Developer tools and the fake provider (FR-035–FR-037)

- **Decision**:
  - `devtools/push/DevFakePushTokenProvider`: `isAvailable = true`; token `dev-fake-push-` + a UUID stored under `dev_fake_push_token` (created on first read; `discard()` removes it; `rotate()` = discard + `PushReceiver.onNewToken()`). Only bound when `AppConfig.isDebugTools` (dev flavor); staging/prod bind `UnavailablePushTokenProvider` (graph test per config).
  - `devtools/push/SimulatePush{Contract,ViewModel,Route,Screen}`: a list of the 12 types + "Unknown type", each with "Post"; ids filled from the phone (first wallet, first scheduled occurrence, first budget; the export id from a fixed random UUID); `Post` builds the data map exactly as the contract, uses fixed generic dev strings (`dev_push_title`, `dev_push_body`), and calls `PushReceiver.onMessage` (same path as a real foreground message). Also "Rotate push address" (exercises `TokenChanged`).
  - Developer tools become reachable from the Account screen in dev builds (`AccountDestination.DevTools`, row shown only when `isDebugTools`).
- **Rationale**: the only way to exercise display and routing without Firebase, and the only push surface Maestro can drive.

## R-16 Logging and privacy (FR-008, FR-038)

- **Decision**: one Kermit tag `push`; messages are the outcome or type name only (`registered`, `unregistered`, `stopped:not_found`, `received:budget_exceeded`). `PushMessage`, `PostedNotification`, `PushRegistrationRecord` and the tap map wrapper override `toString` to print the type only. A log-capture test runs register, rotate, receive, tap and sign-out and asserts no token, id, title or text appears.

## R-17 Testing without devices

- **Decision**: commonTest — parser/collapse/router tables, registration service (fake API, fake provider, fake permission, virtual time), coordinator, sign-out participant and cleaner, receiver (de-dup, drop rules), preferences service, open service, gate, reducers and stores, contract tests against the `notifications.yaml` examples with `MockEngine`. androidHostTest (Robolectric) — channels (ids, importance, private visibility, rename), posting (tag, channel, public version, PendingIntent extras), cancel-all, `PushTapExtras.from`, permission state on API 32/33, the WorkManager scheduler and worker outcomes, `AppGraphTest` additions (provider binding per config, coordinator and participants resolve, no cycle), layering, log capture, Roborazzi for the new screens, sheet and `TimeOfDayField`. Live QA: curl against the local backend once spec 023 is committed, captured answers replayed by `NotificationsLiveReplayTest`. Maestro flows written under `.maestro/017-push/`, deferred.
