# Contract: Screens, copy and deferred E2E

## Navigation

| Key | Opened from | Back to |
|---|---|---|
| `ExportsKey` | Account "Export my data"; Delete account explanation and pending view "Export my data" | caller |
| `ExportRequestKey(walletId: String? = null)` | Exports "New export"; wallet detail "Export this wallet" (preset); "Try again" on a failed export (preset scope) | caller |
| `ExportDetailKey(exportId: String)` | Exports row; after `Created`; "See it" on `EXPORT_IN_PROGRESS` | caller (request screen is replaced by the detail on `Created`) |

## Test tags

| Screen | Tags |
|---|---|
| Account | `account.export` |
| Wallet detail | `walletDetail.export` |
| Exports | `exports.list`, `exports.row.<id>`, `exports.new`, `exports.more`, `exports.refresh`, `exports.empty`, `exports.message` |
| Request | `exportRequest.scope.account`, `exportRequest.scope.wallet`, `exportRequest.wallet`, `exportRequest.explain`, `exportRequest.unsent`, `exportRequest.submit`, `exportRequest.message`, `exportRequest.seeIt` |
| Detail | `exportDetail.status`, `exportDetail.progress`, `exportDetail.size`, `exportDetail.until`, `exportDetail.open`, `exportDetail.retry`, `exportDetail.checkAgain`, `exportDetail.new`, `exportDetail.message` |
| Delete account | `delete.explain.play`, `delete.explain.export`, `delete.plan.loading`, `delete.owners`, `delete.owners.wallet.<id>`, `delete.owners.choice.<walletId>.<userId>`, `delete.owners.next`, `delete.review`, `delete.review.submit`, `delete.scheduled.owners`, `delete.pending`, `delete.pending.change`, `delete.pending.export`, `delete.pending.cancel`, `delete.message` |
| Transaction editor / rule detail | `txEditor.addedBy` (existing), `ruleDetail.author` |

## Copy (English / Arabic — Arabic for owner review)

| Key | English | Arabic |
|---|---|---|
| `account_export` | Export my data | تصدير بياناتي |
| `wallet_export` | Export this wallet | تصدير هذه المحفظة |
| `exports_title` | Exports | عمليات التصدير |
| `exports_new` | New export | تصدير جديد |
| `exports_empty` | No exports in the last 30 days. | لا توجد عمليات تصدير في آخر 30 يومًا. |
| `exports_more` | Show more | عرض المزيد |
| `export_kind_account` | Everything | كل البيانات |
| `export_kind_wallet_gone` | A wallet you no longer have | محفظة لم تعد لديك |
| `export_request_title` | New export | تصدير جديد |
| `export_scope_account` | Everything in my account | كل ما في حسابي |
| `export_scope_wallet` | One wallet | محفظة واحدة |
| `export_explain` | You'll get a ZIP file with your data in JSON files and your transactions in a CSV spreadsheet file. Amounts are in the currency's smallest unit (for example, piastres). Other members appear as "other member", without their names. The file can be downloaded for 24 hours after it's ready, and we'll also email you when it's ready. | ستحصل على ملف ZIP يضم بياناتك في ملفات JSON ومعاملاتك في ملف جدول بيانات CSV. المبالغ بأصغر وحدة للعملة (مثل القروش). يظهر الأعضاء الآخرون باسم "عضو آخر" دون أسمائهم. يمكن تنزيل الملف لمدة 24 ساعة بعد تجهيزه، وسنرسل لك بريدًا إلكترونيًا أيضًا عند تجهيزه. |
| `export_unsent` | Changes not yet sent from this phone aren't included. | التغييرات التي لم تُرسل بعد من هذا الهاتف غير مضمّنة. |
| `export_submit` | Request export | طلب التصدير |
| `export_status_queued` | Waiting to start | في انتظار البدء |
| `export_status_running` | Preparing your file | جارٍ تجهيز ملفك |
| `export_status_ready` | Ready | جاهز |
| `export_status_failed` | Couldn't prepare | تعذّر التجهيز |
| `export_status_expired` | Expired | منتهي |
| `export_status_unknown` | Status unavailable | الحالة غير متاحة |
| `export_requested_at` | Requested %1$s | طُلب في %1$s |
| `export_prepared_at` | Prepared %1$s | جُهّز في %1$s |
| `export_until` | Available until %1$s | متاح حتى %1$s |
| `export_open` | Open download | فتح التنزيل |
| `export_expired_body` | Expired. Files are kept for 24 hours. | انتهت صلاحيته. تُحفظ الملفات لمدة 24 ساعة. |
| `export_still_preparing` | Still preparing. We'll email you when it's ready. | ما زال قيد التجهيز. سنرسل لك بريدًا إلكترونيًا عند تجهيزه. |
| `export_check_again` | Check again | تحقّق مرة أخرى |
| `export_failed_generic` | We couldn't prepare this file. | تعذّر علينا تجهيز هذا الملف. |
| `export_failed_wallet` | This wallet was no longer available when the file was prepared. | لم تعد هذه المحفظة متاحة عند تجهيز الملف. |
| `export_in_progress` | An export is already being prepared. Wait for it to finish before asking for another. | يجري تجهيز تصدير بالفعل. انتظر حتى ينتهي قبل طلب تصدير آخر. |
| `export_see_it` | See it | عرضه |
| `export_limit_hours` | You've asked for the most exports allowed in a day. Try again in %1$d hours. | لقد طلبت الحد الأقصى المسموح به من عمليات التصدير في اليوم. حاول مرة أخرى بعد %1$d ساعات. |
| `export_limit_minutes` | You've asked for the most exports allowed in a day. Try again in %1$d minutes. | لقد طلبت الحد الأقصى المسموح به من عمليات التصدير في اليوم. حاول مرة أخرى بعد %1$d دقيقة. |
| `export_limit_later` | You've asked for the most exports allowed in a day. Try again later. | لقد طلبت الحد الأقصى المسموح به من عمليات التصدير في اليوم. حاول مرة أخرى لاحقًا. |
| `export_wallet_gone` | You no longer have this wallet. | لم تعد هذه المحفظة لديك. |
| `export_gone` | This export is no longer available. | لم يعد هذا التصدير متاحًا. |
| `export_link_missing` | Couldn't open the download. Try again. | تعذّر فتح التنزيل. حاول مرة أخرى. |
| `export_open_failed` | Couldn't open the download. Install or enable a web browser. | تعذّر فتح التنزيل. ثبّت متصفح ويب أو فعّله. |
| `export_offline` | Exports need a connection. | يحتاج التصدير إلى اتصال. |
| `delete_explain_shared` | Shared wallets you own pass to another member; they aren't deleted. Wallets only you use are deleted with your account. Your entries in shared wallets stay and show as "a former member". | تنتقل المحافظ المشتركة التي تملكها إلى عضو آخر ولا تُحذف. تُحذف المحافظ التي تستخدمها وحدك مع حسابك. تبقى إدخالاتك في المحافظ المشتركة وتظهر باسم "عضو سابق". |
| `delete_play_notice` | Deleting your account doesn't cancel a Google Play subscription. Cancel it in Google Play to stop payments. | حذف حسابك لا يلغي اشتراك Google Play. ألغِه من Google Play لإيقاف الدفع. |
| `delete_export_first` | Export my data first | صدّر بياناتي أولًا |
| `delete_plan_loading` | Checking your shared wallets… | جارٍ التحقق من محافظك المشتركة… |
| `delete_owners_title` | New owners | المالكون الجدد |
| `delete_owner_fixed` | %1$s will become the owner. | سيصبح %1$s المالك. |
| `delete_owner_suggested` | Suggested | مقترح |
| `delete_owner_joined` | %1$s · joined %2$s | %1$s · انضم في %2$s |
| `delete_wallet_unknown` | A shared wallet | محفظة مشتركة |
| `delete_member_unknown` | Another member | عضو آخر |
| `delete_review_title` | Review | المراجعة |
| `delete_review_none` | No shared wallet is affected. | لا تتأثر أي محفظة مشتركة. |
| `delete_review_rest` | Other shared wallets follow the usual rule. | تتبع المحافظ المشتركة الأخرى القاعدة المعتادة. |
| `delete_successors_changed` | Your shared wallets changed. Check the new owners again. | تغيّرت محافظك المشتركة. راجع المالكين الجدد مرة أخرى. |
| `delete_reason_only_member` | the only other member | العضو الآخر الوحيد |
| `delete_reason_chosen` | your choice | اختيارك |
| `delete_reason_oldest_editor` | longest-standing editor | أقدم محرر |
| `delete_reason_oldest_viewer` | longest-standing viewer | أقدم مشاهد |
| `delete_pending_title` | Your account will be deleted on %1$s. | سيُحذف حسابك في %1$s. |
| `delete_being_deleted` | Your account is being deleted. | يجري حذف حسابك. |
| `delete_change_owners` | Change new owners | تغيير المالكين الجدد |
| `delete_choices_dropped` | Your choices of new owners were dropped. | أُلغيت اختياراتك للمالكين الجدد. |
| `delete_owners_saved` | New owners saved. | حُفظ المالكون الجدد. |
| `delete_after_30_days` | Your account will be deleted after 30 days. | سيُحذف حسابك بعد 30 يومًا. |
| `tx_added_by_former` | Added by a former member | أضافها عضو سابق |
| `recurring_author_other` | Added by another member | أضافها عضو آخر |
| `recurring_author_former` | Added by a former member | أضافها عضو سابق |
| `error_export_in_progress` (AuthErrorCopy) | = `export_in_progress` | = |

Existing strings reused: `member_unnamed`, `activity_former_member`, `home_cancel_deletion`,
`home_deletion_cancelled_done`, roles, `error_password_incorrect`.

String-scan test: none of the spec 016 strings contains "XLSX", "Excel", "encrypt", "token" (or
"إكسل", "مشفر"); `delete_play_notice` is rendered on the explanation, review and pending view.

## Accessibility

Status text is the live region (`liveRegion = Polite`); refusals are `Assertive`; progress
indicators carry the status text as content description; choices are radio semantics with role and
joined date in the label; all actions ≥ 48 dp; 200 % font screenshots for every screen and step.

## Screenshots (Roborazzi, EN LTR / AR RTL / EN 2.0)

Exports: list with rows of every status, empty, offline. Request: account, wallet chosen, unsent
note, in-progress message, rate limit. Detail: queued, running, ready, failed (generic, wallet),
expired, still preparing, not found. Delete account: explanation (extended), loading plan, new
owners (fixed + choice + unknown wallet), review, scheduled with owners, pending, pending being
deleted, successors changed. Home "being deleted". Transaction caption and rule author "former".

## Deferred E2E (`.maestro/016-export-deletion/`, deferred: E2E on owner's device)

| Flow | Scenario |
|---|---|
| `US1-export-everything.yaml` | US1-1…5: request, wait for Ready (queue worker), Open download hands off to the browser |
| `US1-explain.yaml` | US1-7 copy |
| `US2-export-wallet.yaml` | US2-1/2 from wallet detail (viewer seed) |
| `US3-in-progress.yaml` | US3-1 double request → See it |
| `US3-rate-limit.yaml` | US3-2 (seed: 5 exports in 24 h) |
| `US4-new-owners.yaml` | US4-1…7 with `+shared-owner` seed (wallet A: one viewer; wallet B: editor + viewer) |
| `US5-pending.yaml` | US5-1…3 change owners, cancel |
| `US6-former-member.yaml` | US6-1…3 after the backend purge (time travel + artisan between parts) |

Seeds added to `run-e2e.sh`: `+shared-owner` (owner with the two wallets and partners),
`+export-limit` (five accepted exports), `e2e-between: purge` (travel and run the purge command).
