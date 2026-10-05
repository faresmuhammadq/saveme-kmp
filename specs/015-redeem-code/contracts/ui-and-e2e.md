# Contract: Screens, copy, test tags and E2E seeds

## Navigation

Account → "Plan" (`account.plan`, existing) → Plan screen → "Enter code" (`plan.enterCode`) →
`RedeemCodeKey` → redeem screen. Back or "Done" returns to the Plan screen. A verify-prompt
effect sets the nav host's existing `emailNotVerifiedVisible`.

## Redeem screen (MVI, P-16)

- **Intents**: `Opened`, `CodeChanged(text)` (redacted `toString`), `Redeem`, `TryAgain`, `Done`, `Back`.
- **Effects**: `NavigateBack`, `RequireVerification`.
- **Layout** (top to bottom): top bar "Enter code" with back; explanation text; verify note (only when unverified, `InlineAlert` info); `CodeField` "Redeem code" with supporting text "16 letters and digits, as in your email"; message area (`InlineAlert` error, live region); primary button "Redeem" (shows progress while sending) or "Try again" for offline/in-progress; after success the content is replaced by the confirmation: heading "Code redeemed", governing line, optional redeemed-plan line, optional starts line, optional Google Play note, primary "Done".
- `SecureWindow()` at the top of `RedeemRoute` (FR-029).

## Test tags

| Element | Tag |
|---|---|
| Plan screen button | `plan.enterCode` |
| Code field | `redeem.code` |
| Verify note | `redeem.verifyNote` |
| Redeem button | `redeem.submit` |
| Try again | `redeem.retry` |
| Message | `redeem.message` |
| Confirmation block | `redeem.done` (governing line `redeem.done.governing`, redeemed line `redeem.done.redeemed`, starts line `redeem.done.starts`, Play note `redeem.done.play`) |
| Done button | `redeem.doneButton` |

## Copy (new keys, English and Arabic; Western digits; no "InstaPay", price, payment path, "token")

| Key | EN | AR |
|---|---|---|
| `plan_enter_code` | Enter code | أدخل رمزًا |
| `redeem_title` | Enter code | إدخال رمز |
| `redeem_intro` | Enter the code from your email. It works only on the account with the email address it was sent to. | أدخل الرمز الموجود في بريدك الإلكتروني. يعمل الرمز فقط على الحساب الذي يحمل البريد الإلكتروني الذي أُرسل إليه. |
| `redeem_field_label` | Redeem code | رمز الاسترداد |
| `redeem_field_hint` | 16 letters and digits, as in your email | 16 حرفًا ورقمًا كما في بريدك |
| `redeem_verify_note` | Verify your email address first. Codes work only on verified accounts. | أكّد بريدك الإلكتروني أولًا. تعمل الرموز على الحسابات المؤكَّدة فقط. |
| `redeem_submit` | Redeem | استرداد |
| `redeem_retry` | Try again | حاول مرة أخرى |
| `redeem_offline` | You need a connection to redeem a code. | تحتاج إلى اتصال بالإنترنت لاسترداد الرمز. |
| `redeem_not_valid` | This code isn't valid. Check it and try again. | هذا الرمز غير صالح. تحقّق منه وحاول مرة أخرى. |
| `redeem_invalid_format` | This isn't a valid code. Codes have 16 letters and digits. | هذا ليس رمزًا صالحًا. يتكوّن الرمز من 16 حرفًا ورقمًا. |
| `redeem_signed_in_as` | You're signed in as %1$s. | أنت مسجّل الدخول باسم %1$s. |
| `redeem_rate_limited_minutes` | Too many attempts. Try again in %1$d minutes. | محاولات كثيرة. حاول مرة أخرى بعد %1$d دقيقة. |
| `redeem_rate_limited_minute` | Too many attempts. Try again in a minute. | محاولات كثيرة. حاول مرة أخرى بعد دقيقة. |
| `redeem_rate_limited_later` | Too many attempts. Try again later. | محاولات كثيرة. حاول مرة أخرى لاحقًا. |
| `redeem_in_progress` | Still checking this code. Try again in a moment. | ما زلنا نتحقق من هذا الرمز. حاول مرة أخرى بعد لحظات. |
| `redeem_done_title` | Code redeemed | تم استرداد الرمز |
| `redeem_done_now_on_until` | You're now on %1$s until %2$s. | أنت الآن على خطة %1$s حتى %2$s. |
| `redeem_done_now_on_no_end` | You're now on %1$s, with no end date. | أنت الآن على خطة %1$s بلا تاريخ انتهاء. |
| `redeem_done_current_stays_until` | Your current plan, %1$s, stays in place because it ends later, on %2$s. | تبقى خطتك الحالية %1$s سارية لأنها تنتهي لاحقًا في %2$s. |
| `redeem_done_current_stays_no_end` | Your current plan, %1$s, stays in place because it has no end date. | تبقى خطتك الحالية %1$s سارية لأنها بلا تاريخ انتهاء. |
| `redeem_done_redeemed_until` | The redeemed plan, %1$s, runs until %2$s. | تستمر الخطة المستردّة %1$s حتى %2$s. |
| `redeem_done_redeemed_no_end` | The redeemed plan, %1$s, has no end date. | الخطة المستردّة %1$s بلا تاريخ انتهاء. |
| `redeem_done_starts_on` | Its billing period starts on %1$s. | تبدأ فترة الفوترة الخاصة بها في %1$s. |
| `redeem_done_play_renews` | Google Play will keep charging for your subscription until you cancel it in Google Play. | سيستمر Google Play في تحصيل رسوم اشتراكك إلى أن تلغيه من Google Play. |
| `redeem_done_button` | Done | تم |
| `error_redeem_code_email_mismatch`, `error_redeem_code_used`, `error_redeem_code_expired` | see redeem-api-client.md | |

Arabic plural forms for minutes use the existing plural handling if the strings file has one;
otherwise the sentence form above (Western digits) — assumed default for owner review.
A string-scan test asserts no `redeem_*`/`plan_enter_code` string (either language) contains
"InstaPay", "إنستاباي", a currency code, "token", "voucher", "coupon", "promo".

## Accessibility (FR-030)

48 dp buttons; the field's label is announced; the code is read as separate characters; the
message area is a polite live region (and `error` semantics on the field for format messages);
the confirmation heading is a heading; nothing conveyed by colour alone; 200 % font screenshots.

## E2E (deferred: E2E on owner's device)

Flows in `.maestro/015-redeem-code/`, each with a header comment
`# deferred: E2E on owner's device (owner policy 2026-10-04)`.

| Flow | Seed | Proves |
|---|---|---|
| `US1-redeem.yaml` | `verified-user+redeem-code` | type `${E2E_CODE}` lower-case without hyphens → grouped; Redeem → "Code redeemed", "now on Plus"; Done → Plan shows Plus (brief checkpoint) |
| `US2-current-stays.yaml` | `verified-user+plus-lifetime+redeem-code` | confirmation says the current plan stays (no end date) and the redeemed plan's end |
| `US3-other-address.yaml` | `verified-user+redeem-code-other` | mismatch message, own address shown, other address absent |
| `US3-used.yaml` | `verified-user+redeem-code-used` | "already been used" |
| `US3-expired.yaml` | `verified-user+redeem-code-expired` | "has expired" |
| `US3-not-valid.yaml` | `verified-user` | `AAAA-BBBB-CCCC-DDDD` → "isn't valid" |
| `US3-unverified.yaml` | `unverified-user` | verify note; Redeem opens the verify prompt; nothing sent |
| `US3-offline.part1/part2/part3.yaml` | `verified-user+redeem-code`; part 2 `e2e-between: network-off`, part 3 `e2e-between: network-on` | part 2: "You need a connection" + Try again; part 3: Try again redeems with the same attempt |

Seeds added to `run-e2e.sh` (research R-12): `+redeem-code`, `+redeem-code-other`,
`+redeem-code-used`, `+redeem-code-expired`, composable after the existing seeds
(`verified-user+plus-lifetime+redeem-code`). Env: `E2E_CODE`; `MAILPIT_API` (default
`http://mailpit.test/api/v1`).
