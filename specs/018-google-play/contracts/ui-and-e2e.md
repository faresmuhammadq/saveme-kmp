# Contract: Plan screen additions, copy and E2E

All on the existing Plan screen (spec 005, `feature.billing.ui.plan`); no new screen. Built from
design-system components only (`InlineAlert`, `SecondaryButton`, `AppTextButton`, `AppSnackbarHost`,
`PlanCard`); no new component (P-07). Arabic copy is an assumed default for owner review.

## Layout (top to bottom)

1. Stale note (existing).
2. **Payment problem** `InlineAlert(Warning)` (`plan.play.problem`) with "Fix payment" (`plan.play.fixPayment`) — grace, or hold seen this run; paused → `InlineAlert(Info)` with "Manage in Google Play".
3. **Pending** `InlineAlert(Info)` (`plan.play.pending`).
4. **Another account** `InlineAlert(Info)` (`plan.play.anotherAccount`).
5. Current plan (existing) + "Test purchase" label (`plan.current.test`) + "Manage in Google Play" text button (`plan.play.manage`) when the governing subscription is Google Play's.
6. Catalogue (existing); card price from Google Play (`plan.card.N.price`); US6 only: "Subscribe" (`plan.card.N.subscribe`).
7. Billing actions (the reserved place): "Restore purchases" (`plan.restore`, `SecondaryButton`, progress while running) above "Enter code" (`plan.enterCode`, existing). Android only.
8. Restore result / refusal `InlineAlert` under the actions (`plan.restore.result`, `plan.restore.retry` for "Try again").
9. Snackbar for "You're on <plan>." (`plan.activated`).

State is never shown by colour alone (each alert has its text and icon); targets ≥ 48 dp; 200 %
font wraps; RTL mirrors (P-06, P-08). Dates in the profile time zone (P-05).

## Copy (EN / AR)

| Key | English | Arabic |
|---|---|---|
| `plan_restore` | Restore purchases | استعادة المشتريات |
| `plan_play_manage` | Manage in Google Play | الإدارة في Google Play |
| `plan_play_fix_payment` | Fix payment | إصلاح الدفع |
| `plan_play_grace` | Google Play couldn't take your payment. Fix it by %1$s to keep %2$s. | تعذّر على Google Play تحصيل الدفعة. أصلحها قبل %1$s للاحتفاظ بخطة %2$s. |
| `plan_play_hold` | Your Google Play subscription is on hold because of a payment problem. Fix your payment to get %1$s back. | اشتراكك في Google Play متوقف مؤقتًا بسبب مشكلة في الدفع. أصلح الدفع لاستعادة خطة %1$s. |
| `plan_play_paused` | Your Google Play subscription is paused. You can resume it in Google Play. | اشتراكك في Google Play متوقف مؤقتًا. يمكنك استئنافه من Google Play. |
| `plan_play_pending` | Payment pending in Google Play. Plus starts when Google Play confirms the payment. | الدفع قيد الانتظار في Google Play. تبدأ خطة Plus عندما يؤكد Google Play الدفع. |
| `billing_play_another_account` | A Google Play purchase on this phone belongs to another account. Sign in with the account that bought it to use it. | عملية شراء من Google Play على هذا الهاتف تخص حسابًا آخر. سجّل الدخول بالحساب الذي اشتراها لاستخدامها. |
| `billing_play_not_verified` | Google Play couldn't confirm a purchase. Nothing was charged to your plan. | تعذّر على Google Play تأكيد عملية شراء. لم يتغير شيء في خطتك. |
| `plan_current_test` | Test purchase | عملية شراء تجريبية |
| `plan_activated` | You're on %1$s. | أنت الآن على خطة %1$s. |
| `plan_restore_done` | Your purchases are restored. | تمت استعادة مشترياتك. |
| `plan_restore_nothing` | No Google Play purchases to restore for this Google account. | لا توجد مشتريات من Google Play لاستعادتها لهذا الحساب على Google. |
| `plan_restore_pending` | A payment is still pending in Google Play. | لا تزال دفعة قيد الانتظار في Google Play. |
| `plan_restore_unavailable` | Google Play couldn't be reached for some purchases. Try again later. | تعذّر الوصول إلى Google Play لبعض المشتريات. حاول مرة أخرى لاحقًا. |
| `plan_play_not_available` | Google Play isn't available on this phone. | Google Play غير متاح على هذا الهاتف. |
| `plan_play_open_failed` | Couldn't open Google Play. | تعذّر فتح Google Play. |
| `plan_restore_offline` | You're offline. Connect to the internet and try again. | أنت غير متصل. اتصل بالإنترنت وحاول مرة أخرى. |
| `plan_restore_rate_limited` | Too many tries. Try again in %1$s. | محاولات كثيرة. حاول مرة أخرى بعد %1$s. |
| `plan_restore_busy` | Google Play or our service is busy. Try again in a moment. | Google Play أو خدمتنا مشغولة. حاول مرة أخرى بعد قليل. |
| `plan_try_again` (existing or new) | Try again | حاول مرة أخرى |
| **US6 only** `plan_subscribe` | Subscribe | اشترك |
| US6 `plan_subscribe_offline` | Connect to the internet to subscribe. | اتصل بالإنترنت للاشتراك. |
| US6 `plan_subscribe_unavailable` | This plan isn't available on Google Play right now. | هذه الخطة غير متاحة على Google Play حاليًا. |
| US6 `plan_subscribe_failed` | Google Play couldn't start the purchase. Try again. | تعذّر على Google Play بدء عملية الشراء. حاول مرة أخرى. |

Generic and rate-limit wait wording reuse spec 015's helpers (minutes sentence form; request id on
the generic error). Never in any string: "token", "acknowledge", "InstaPay", "order", a price
literal, "verify". `RedeemStringsTest`-style scan extended to these keys.

## Effects

| Effect | Route handling |
|---|---|
| `OpenUrl(url)` | `LocalUriHandler.openUri(url)`; on failure → snackbar `plan_play_open_failed` |
| `ShowActivated(planName)` | snackbar `plan_activated` |
| `OpenRedeem` | existing |

## E2E (Maestro) — written, **deferred: E2E on owner's device**

Directory `.maestro/018-google-play/`; every flow runs on the dev build with the Dev tools
"Google Play (scripted)" switch on and the local backend on `BILLING_PLAY_DRIVER=fake` with
`BILLING_PLAY_FAKE_SCRIPT` scripting the same tokens. `run-e2e.sh` gains seeds that write the fake
script per flow (tokens `e2e-<flow>-…`) and point it at the fresh account's reference.

| Flow | Covers |
|---|---|
| `US1-background-verify.yaml` | script an unacknowledged purchased `plus_monthly`; open the app → Plan shows Plus |
| `US1-pending-then-purchased.yaml` | pending → pending note; flip to purchased → Plus |
| `US2-restore-tap.yaml` | sign out, sign in, tap Restore → "restored" |
| `US2-restore-nothing.yaml` | no purchases → "nothing to restore" |
| `US3-another-account.yaml` | account A's purchase, sign in as B → note |
| `US4-grace-fix-payment.yaml` | seed grace (backend), Plan shows warning and Fix payment (assert the button only; Google Play not opened) |
| `US5-prices.yaml` | scripted product price shows on the card |

No flow buys through Google Play; real purchases are the owner's manual checkpoint with a license
tester after G1.
