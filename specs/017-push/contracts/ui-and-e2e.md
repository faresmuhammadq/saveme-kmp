# Contract: Screens, strings, test tags and E2E flows

All screens follow P-16 (`…Contract.kt`, `…ViewModel`, `…Route`, stateless `…Screen`) and are built
from design-system components (P-07). Arabic is the assumed default copy for owner review.
Western digits everywhere; times in LTR runs.

## 1. Navigation keys (`navigation/Routes.kt`, append-only block "Push (spec 017)")

| Key | Opens |
|---|---|
| `NotificationSettingsKey` | Notifications screen |
| `NotificationOpenKey(route: PushRoute)` | "Opening…" screen (`toString` = target type only) |
| `InvitationNoticeKey` | invitation notice |
| `SimulatePushKey` | developer tool (dev builds) |

Account gains `AccountDestination.Notifications` (row shown when `PushTokenProvider.isAvailable`)
and `AccountDestination.DevTools` (row shown when `AppConfig.isDebugTools`).

## 2. Notifications screen (`feature/notifications/ui/settings/`)

State: `loading`, `notLoaded: LoadMessage?`, `read: NotificationPreferences?`, `edited: NotificationPreferences?`,
`phone: PermissionStatus`, `timeZoneId: String?`, `saving`, `message: SettingsMessage?`,
`discardConfirmVisible`. Derived: `dirty`, `valid`, `canSave`.

Intents: `Opened`, `Resumed(status)`, `TryAgain`, `CategoryToggled(category, on)`,
`QuietHoursToggled(on)`, `StartChosen(minutes)`, `EndChosen(minutes)`, `Save`, `Back`,
`DiscardConfirmed`, `DiscardDismissed`, `TurnOnClicked`, `PermissionResult(granted)`,
`OpenPhoneSettingsClicked`, `MessageShown`.

Effects: `RequestPermission`, `OpenPhoneSettings`, `NavigateBack`, `ShowSaved`.

| Element | Test tag | EN | AR |
|---|---|---|---|
| Title | `notifications.title` | Notifications | الإشعارات |
| Phone card | `notifications.phoneOff` | Notifications are off on this phone. Turn them on to be told when something needs you. | الإشعارات متوقفة على هذا الهاتف. فعّلها لنخبرك عندما يحتاج شيء إلى انتباهك. |
| Turn on | `notifications.turnOn` | Turn on | تفعيل |
| Open settings | `notifications.openSettings` | Open settings | فتح الإعدادات |
| Section | — | What to tell me about | ما الذي نخبرك به |
| Switch recurring | `notifications.category.recurring_due` | Recurring items due · A recurring item needs confirming, or could not be recorded automatically. | عناصر متكررة مستحقة · عنصر متكرر يحتاج إلى تأكيد، أو تعذّر تسجيله تلقائيًا. |
| Switch budgets | `notifications.category.budget_exceeded` | Budget alerts · A budget went over its limit this period. | تنبيهات الميزانية · تجاوزت ميزانية حدّها في هذه الفترة. |
| Switch sharing | `notifications.category.sharing` | Shared wallets · An invitation, a role change, a removal, or a wallet passed to you. | المحافظ المشتركة · دعوة، أو تغيير في الدور، أو إزالة، أو محفظة انتقلت إليك. |
| Switch billing | `notifications.category.billing` | Plan and billing · Your plan ends soon, or has ended. | الخطة والفوترة · تنتهي خطتك قريبًا، أو انتهت. |
| Switch exports | `notifications.category.export_ready` | Exports · An export you asked for is ready. | التصدير · ملف التصدير الذي طلبته جاهز. |
| Switch deletion | `notifications.category.deletion_reminder` | Account deletion · Your account is about to be deleted. | حذف الحساب · حسابك على وشك أن يُحذف. |
| Quiet hours switch | `notifications.quiet` | Quiet hours · We hold notifications during these hours and send them when they end. | ساعات الهدوء · نؤجل الإشعارات خلال هذه الساعات ونرسلها عند انتهائها. |
| From | `notifications.quiet.start` | From | من |
| To | `notifications.quiet.end` | To | إلى |
| Invalid range | `notifications.quiet.error` | Start and end must be different. | يجب أن تختلف بداية الساعات عن نهايتها. |
| Time zone note | `notifications.timeZone` | Times are in your profile's time zone: %1$s | الأوقات حسب المنطقة الزمنية في ملفك الشخصي: %1$s |
| Scope note | `notifications.scope` | These settings apply to every phone you're signed in on. Emails are not affected. | تنطبق هذه الإعدادات على كل هاتف سجّلت الدخول عليه. ولا تتأثر رسائل البريد الإلكتروني. |
| Phone settings row | `notifications.phoneSettings` | Notification settings on this phone | إعدادات الإشعارات على هذا الهاتف |
| Save | `notifications.save` | Save | حفظ |
| Saved snackbar | — | Saved | تم الحفظ |
| Conflict | `notifications.message` | These settings were changed on another device. Check them and save again. | تغيّرت هذه الإعدادات على جهاز آخر. راجعها واحفظ مرة أخرى. |
| Rate limit (n) | `notifications.message` | Too many changes. Try again in %1$d minutes. | تغييرات كثيرة. حاول مرة أخرى بعد %1$d دقيقة. |
| Rate limit (1) | `notifications.message` | Too many changes. Try again in a minute. | تغييرات كثيرة. حاول مرة أخرى بعد دقيقة. |
| Rate limit (none) | `notifications.message` | Too many changes. Try again later. | تغييرات كثيرة. حاول مرة أخرى لاحقًا. |
| Offline | `notifications.message` | Needs a connection. Your changes are still here. | يتطلب اتصالًا بالإنترنت. ما زالت تغييراتك هنا. |
| Not loaded (offline) | `notifications.notLoaded` | Needs a connection to show your notification settings. | يتطلب اتصالًا بالإنترنت لعرض إعدادات الإشعارات. |
| Try again | `notifications.tryAgain` | Try again | حاول مرة أخرى |
| General failure | `notifications.message` | existing `error_generic_reference` (request id) or `error_generic` | existing |
| Discard dialog | `notifications.discard` | Discard your changes? · Discard · Keep editing | تجاهل التغييرات؟ · تجاهل · متابعة التعديل |
| Account row | `account.notifications` | Notifications | الإشعارات |

## 3. "Opening…" screen (`feature/notifications/ui/open/`)

| Element | Test tag | EN | AR |
|---|---|---|---|
| Progress + text | `open.progress` | Opening… | جارٍ الفتح… |
| Cancel | `open.cancel` | Cancel | إلغاء |

State: `waiting: Boolean`. Intents: `Started`, `Cancel`. Effects: `Replace(target)`, `Close`.

## 4. Invitation notice (`feature/notifications/ui/invitation/`)

| Element | Test tag | EN | AR |
|---|---|---|---|
| Title | `invitationNotice.title` | You've been invited to a shared wallet | تمت دعوتك إلى محفظة مشتركة |
| Body | `invitationNotice.body` | The invitation was sent to %1$s. Open the email and tap the link in it to see and answer the invitation. | أُرسلت الدعوة إلى %1$s. افتح الرسالة واضغط الرابط الموجود فيها لعرض الدعوة والرد عليها. |
| OK | `invitationNotice.ok` | OK | حسنًا |

## 5. Explanation sheet (global, `feature/notifications/ui/prompt/`)

| Element | Test tag | EN | AR |
|---|---|---|---|
| Title | `pushPrompt.title` | Get told when something needs you | اعرف عندما يحتاج شيء إلى انتباهك |
| Body | `pushPrompt.body` | We'll let you know when a recurring item is due, a budget goes over its limit or a shared wallet changes. Notifications never show amounts. | سنخبرك عندما يستحق عنصر متكرر، أو تتجاوز ميزانية حدّها، أو تتغير محفظة مشتركة. لا تعرض الإشعارات أي مبالغ. |
| Turn on | `pushPrompt.turnOn` | Turn on | تفعيل |
| Not now | `pushPrompt.notNow` | Not now | ليس الآن |

## 6. Channels and posted text (string resources)

Channel names as data-model §5; descriptions = the switch descriptions above. Public version:
"New notification" / "إشعار جديد". Developer tool generic text: "Test notification" / "إشعار تجريبي",
"This was posted from Developer tools." / "تم نشر هذا من أدوات المطوّر.".

## 7. Developer tool (`devtools/push/`, dev builds only)

| Element | Test tag | EN |
|---|---|---|
| Account row | `account.devTools` | Developer tools |
| Menu item | `devtools.simulatePush` | Simulate a notification |
| Row per type | `simulatePush.type.<type>` | the wire type, plus "(not on this phone)" when an id is random |
| Post | `simulatePush.post.<type>` | Post |
| Rotate | `simulatePush.rotate` | Rotate push address |

(Developer strings are resources in both languages, P-06.1; Arabic mirrors the English.)

## 8. Design-system addition

`TimeOfDayField` (research R-13) in `designsystem/component/Pickers.kt`, catalogue entry in the
pickers section, screenshots EN LTR, AR RTL, font 2.0: closed with a value, open sheet scrolled to
the value, disabled, with an error.

## 9. Screenshot states (Roborazzi, EN / AR / EN 2.0)

Notifications: loading, not loaded, defaults, edited dirty, invalid range, saving, conflict
message, rate-limited, phone off (askable), phone off (blocked). "Opening…". Invitation notice.
Explanation sheet. Simulate tool (dev).

## 10. E2E flows (`.maestro/017-push/`, every file starts `# deferred: E2E on owner's device`)

Device prerequisites: dev build; the simulate tool for display and taps (Maestro cannot receive a
real push); Android 13+ for the permission flows.

| Flow | Covers |
|---|---|
| `US1-permission-after-budget.yaml` | create first budget → sheet → Turn on → allow (system dialog) |
| `US1-not-now-is-final.yaml` | sheet → Not now → second budget → no sheet |
| `US1-registered-after-signin.yaml` | sign in → registration visible via api.js (backend admin count) |
| `US2-route-<type>.yaml` × 12 | simulate type → open shade → tap → expected screen tag |
| `US2-route-unknown.yaml` | unknown type → Home |
| `US2-signed-out-tap.yaml` | post, sign out (shade cleared) |
| `US3-edit-and-save.yaml` | toggle budget off, 23:00–07:00, save, reopen → persisted |
| `US3-conflict.yaml` | api.js saves from another session → save → conflict message, values replaced |
| `US3-offline.yaml` | airplane mode → open → Needs a connection |
| `US4-collapse.yaml` | post `budget_exceeded` twice same id → one notification |
| `US4-cleared-on-signout.yaml` | post two → sign out → shade empty |
| `US5-tool-absent-in-prod.yaml` | documentation-only check (prod build has no row) |

`common/open-notifications.yaml` (Account → Notifications) and `common/simulate-push.yaml`
(Account → Developer tools → Simulate a notification → type → Post → open shade → tap).
`api.js` gains `notificationPreferences`, `saveNotificationPreferences`.

No new seed is needed in `.specify/scripts/bash/run-e2e.sh`: every flow signs in with the runner's `verified-user` account. The conflict flow (`US3-conflict`) saves the stored preferences from a second session through `api.js` (`saveNotificationPreferences`, slot B), then saves in the app. Other additions to `api.js`: `notificationPreferences` (reads the stored values) and `expectCurrentSession`; `common/open-notifications.yaml`, `common/open-shade.yaml` (pulls the shade with a swipe from the top edge) and `common/simulate-push.yaml` are the shared subflows. Flows that open a record (`occurrence_due`, `budget_exceeded`, the wallet types) seed it first through the existing `seed-*` subflows, so the tool fills real ids.
