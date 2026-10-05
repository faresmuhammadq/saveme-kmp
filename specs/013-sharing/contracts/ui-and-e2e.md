# Contract: Screens, copy and E2E flows

Every screen follows P-16 (`Contract.kt` with State/Intent/Effect/Change + `reduce`, one
`MviViewModel`, `…Route`, stateless `…Screen`), built from `designsystem` only (P-07), screenshot
tests in EN LTR, AR RTL and font scale 2.0.

## S1 Members (`WalletMembersKey(walletId)`, `feature.sharing.ui.members`)

- **State**: `walletName`, `myRole`, `members: List<MemberView>`, `invitations: List<InvitationView>`, `inviteCheck`, `planNote: Int?`, `busyUserId: String?`, `confirm: MembersConfirm?` (`Remove(member)`, `ChangeRole(member, to)`, `Withdraw(invitation)`, `Leave(waitingCount)`), `offlineUpdatedAt`.
- **Layout**: members section (`MemberRow` + `RolePill`; "You"); owner row has no actions; for the owner, each other member opens a sheet "Make editor" / "Make viewer" / "Remove from wallet". Pending invitations section: address or "Link", role, "Expires <date>" / "Expired" / "Waiting to sync" / "Not sent: <reason>"; owner actions "Withdraw", and for links "Share" / "Copy" (link held) or "Replace link". Footer: "Invite" (owner; disabled with the gate's explanation when not allowed, with "See your plan" for plan reasons); plan note (FR-028); "Leave wallet" (non-owner) or "To leave, transfer ownership first" (owner, when other members exist).
- **Effects**: `OpenInvite(walletId)`, `OpenPlan`, `Share(text)`, `Copy(text)` (redacted `toString`), `ShowMessage(MembersMessage)`, `Left(walletName)`, `ShowVerifyPrompt`.
- **Tags**: `members.list`, `members.row.<userId>`, `members.invite`, `members.invitation.<id>`, `members.leave`, `members.planNote`.

## S2 Invite (`InviteKey(walletId, replacing)`, `feature.sharing.ui.invite`)

- **State**: `channel` (Email default | Link), `role` (Viewer default), `email`, `emailError`, `check: InviteCheck`, `sending`, `confirmSupersede`, `linkReadyFor: String?` (invitation id whose link arrived), `waitingFor: String?`.
- **Flow**: Email → "Send invitation" → queued → closes with "Invitation saved. It will be sent when you're online." or "Invitation sent." when online. Link → "Create link" → queued; waits for the link (spinner "Getting the link…"; offline: "The link will be ready when you're back online. You can share it from Members."); when ready: "Share" and "Copy" (+ "Done").
- **Tags**: `invite.channel.email`, `invite.channel.link`, `invite.role.editor`, `invite.role.viewer`, `invite.email`, `invite.send`, `invite.share`, `invite.copy`.

## S3 Invitation (`InvitationKey(token)`, `feature.sharing.ui.invitation`)

- **Modes**: `SignedOut` ("Sign in or create an account to answer this invitation", "Sign in", "Create an account"); `Loading`; `Ready(preview)` ("<inviter> invited you to <wallet>", role + one line, "Until <date>", "Join", "Decline", "Not now"); `AlreadyMember` ("Open wallet"); `Unavailable`; `InvalidLink`; `Offline` ("Try again"); `TooMany(minutes)`; `OwnerPlanFull`; `Failed(requestId)`.
- **Effects**: `OpenWallet(walletId)`, `NavigateSignIn`, `NavigateRegister`, `Close(message?)`.
- **Tags**: `invitation.join`, `invitation.decline`, `invitation.later`, `invitation.signIn`, `invitation.register`, `invitation.message`.

## S4 Activity (`WalletActivityKey(walletId)`, `feature.sharing.ui.activity`)

- **State**: `items`, `nextCursor`, `loading`, `loadingMore`, `error: ActivityError?`. Day headers (person's time zone), rows "<actor> <action> <subject>" + time; note "Activity covers the last 90 days". Tapping a transaction entry → `OpenTransaction(id, walletId)`.
- **Tags**: `activity.list`, `activity.row.<id>`, `activity.retry`.

## Changed screens

- **Wallet detail (C4)**: members section gains "Manage members" (owner) / "See members" with "(n pending)"; "Activity" row; viewer note (FR-027); "Leave wallet" moves to the members screen; `joining` state "Getting the wallet…" (never "Gone" while joining); closes with "You no longer have access to this wallet" on access loss and "You left <wallet>" after a leave.
- **Transactions list (C6)**: viewer note under the balance for viewers.
- **Transactions / recurring refusals**: `FORBIDDEN` copy "You can now only view this wallet." (R-14).
- **Home (auth)**: card "You have an invitation to answer" + "Open" (`home.invitation`).

## Copy (EN / AR; Arabic for owner review)

| Key | English | Arabic |
|---|---|---|
| members_title | Members | الأعضاء |
| members_manage | Manage members | إدارة الأعضاء |
| members_see | See members | عرض الأعضاء |
| members_pending_count | %1$d pending | %1$d معلّقة |
| invite_action | Invite | دعوة |
| invite_by_email | By email | بالبريد الإلكتروني |
| invite_by_link | Share a link | مشاركة رابط |
| invite_role_editor_hint | Can add and change transactions | يمكنه إضافة المعاملات وتعديلها |
| invite_role_viewer_hint | Can view only | يمكنه العرض فقط |
| invite_send | Send invitation | إرسال الدعوة |
| invite_saved_offline | Invitation saved. It will be sent when you're online. | حُفظت الدعوة وستُرسل عند الاتصال. |
| invite_link_wait_offline | The link will be ready when you're back online. You can share it from Members. | سيكون الرابط جاهزًا عند عودة الاتصال، ويمكنك مشاركته من صفحة الأعضاء. |
| invite_share | Share | مشاركة |
| invite_copy | Copy | نسخ |
| invite_copied | Copied | تم النسخ |
| invite_share_text | %1$s: I'd like to share a wallet with you. Open this link to join: %2$s | %1$s: أود مشاركة محفظة معك. افتح هذا الرابط للانضمام: %2$s |
| invite_supersede | %1$s already has a pending invitation. Send a new one? The earlier one will stop working. | لدى %1$s دعوة معلّقة بالفعل. هل ترسل دعوة جديدة؟ ستتوقف الدعوة السابقة. |
| invite_own_address | That's your own address. | هذا عنوانك أنت. |
| invite_email_invalid | Enter a valid email address. | أدخل عنوان بريد إلكتروني صحيحًا. |
| gate_not_included | Sharing isn't included in your plan. | المشاركة غير متاحة في خطتك. |
| gate_full | This wallet has reached the %1$d people your plan allows. Withdrawing a pending invitation frees a place. | وصلت هذه المحفظة إلى %1$d أشخاص تسمح بهم خطتك. سحب دعوة معلّقة يُفرغ مكانًا. |
| gate_see_plan | See your plan | عرض خطتك |
| invitation_pending | Pending | معلّقة |
| invitation_expires | Expires %1$s | تنتهي في %1$s |
| invitation_expired | Expired | منتهية |
| invitation_link | Link | رابط |
| invitation_withdraw | Withdraw | سحب الدعوة |
| invitation_withdraw_confirm | Withdraw this invitation? Its link will stop working. | هل تسحب هذه الدعوة؟ سيتوقف رابطها عن العمل. |
| invitation_link_gone | This link can't be shown again. | لا يمكن عرض هذا الرابط مرة أخرى. |
| invitation_replace_link | Replace link | استبدال الرابط |
| invitation_not_sent | Not sent | لم تُرسل |
| join_title | %1$s invited you to %2$s | دعاك %1$s إلى %2$s |
| join_someone | Someone | شخص ما |
| join_until | Until %1$s | حتى %1$s |
| join_action | Join | انضمام |
| join_decline | Decline | رفض |
| join_decline_confirm | Decline this invitation? | هل ترفض هذه الدعوة؟ |
| join_later | Not now | ليس الآن |
| join_declined | Invitation declined | رُفضت الدعوة |
| join_already | You're already in this wallet. | أنت عضو في هذه المحفظة بالفعل. |
| join_open_wallet | Open wallet | فتح المحفظة |
| join_signed_out | Sign in or create an account to answer this invitation. | سجّل الدخول أو أنشئ حسابًا للرد على هذه الدعوة. |
| join_unavailable | This invitation can't be opened. It may have ended, been used or withdrawn, or be meant for another account. | لا يمكن فتح هذه الدعوة. ربما انتهت أو استُخدمت أو سُحبت، أو أنها لحساب آخر. |
| join_invalid_link | This link isn't a valid invitation. | هذا الرابط ليس دعوة صالحة. |
| join_owner_plan_full | The wallet owner's plan doesn't allow another person in this wallet right now. | خطة مالك المحفظة لا تسمح بشخص آخر في هذه المحفظة حاليًا. |
| join_too_many | Too many attempts. Try again in %1$d minutes. | محاولات كثيرة. حاول مرة أخرى بعد %1$d دقيقة. |
| needs_connection | Needs a connection. | يتطلب اتصالًا بالإنترنت. |
| home_invitation | You have an invitation to answer | لديك دعوة بانتظار ردك |
| wallet_joining | Getting the wallet… | جارٍ جلب المحفظة… |
| member_make_editor | Make editor | اجعله محررًا |
| member_make_viewer | Make viewer | اجعله مشاهدًا |
| member_remove | Remove from wallet | إزالة من المحفظة |
| member_remove_confirm | Remove %1$s? They will lose access to this wallet. What their phone already downloaded may stay on it. | هل تزيل %1$s؟ سيفقد الوصول إلى هذه المحفظة، وقد يبقى على هاتفه ما نزّله من قبل. |
| member_changed | This member changed. Look again. | تغيّر هذا العضو. راجع مرة أخرى. |
| member_only_owner | Only the owner can do this. | المالك وحده يمكنه ذلك. |
| member_gone | This member is no longer in the wallet. | لم يعد هذا العضو في المحفظة. |
| member_owner_role | Ownership moves only by transfer. | لا تنتقل الملكية إلا بالنقل. |
| leave_action | Leave wallet | مغادرة المحفظة |
| leave_confirm | Leave %1$s? It will be removed from this phone. | هل تغادر %1$s؟ ستُحذف من هذا الهاتف. |
| leave_confirm_waiting | %1$d changes in this wallet haven't synced and will be discarded. | لم تتم مزامنة %1$d من التغييرات في هذه المحفظة وستُتجاهل. |
| leave_owner_note | To leave, transfer ownership first. | لمغادرة المحفظة، انقل ملكيتها أولًا. |
| leave_done | You left %1$s | غادرت %1$s |
| access_lost | You no longer have access to this wallet. | لم يعد لديك وصول إلى هذه المحفظة. |
| viewer_note | You can view this wallet but not change it. Only its owner can change your role. | يمكنك عرض هذه المحفظة دون تعديلها. المالك وحده يمكنه تغيير دورك. |
| viewer_note_plan | The owner's plan changed, so you can now view this wallet but not change it. | تغيّرت خطة المالك، لذا يمكنك الآن عرض هذه المحفظة دون تعديلها. |
| refusal_now_viewer | You can now only view this wallet. | يمكنك الآن عرض هذه المحفظة فقط. |
| plan_note | Your plan allows %1$d people in a wallet. The others can view it but not change it. | تسمح خطتك بـ%1$d أشخاص في المحفظة. يمكن للباقين عرضها دون تعديلها. |
| activity_title | Activity | النشاط |
| activity_window | Activity covers the last 90 days. | يشمل النشاط آخر 90 يومًا. |
| activity_offline | Activity needs a connection. | يتطلب النشاط اتصالًا بالإنترنت. |
| activity_automatic | Automatically | تلقائيًا |
| activity_former_member | A former member | عضو سابق |
| activity_* (one per action) | added, changed, deleted, restored, archived, transferred ownership to, confirmed, skipped, joined as, left, removed, changed the role of … to, became a viewer because of the owner's plan, invited someone as, withdrew an invitation | أضاف، عدّل، حذف، استعاد، أرشف، نقل الملكية إلى، أكّد، تخطّى، انضم بصفة، غادر، أزال، غيّر دور … إلى، أصبح مشاهدًا بسبب خطة المالك، دعا شخصًا بصفة، سحب دعوة |
| activity_subject_* | a transaction, a recurring rule, a payment, a member, an invitation as %1$s, the wallet | معاملة، قاعدة متكررة، دفعة، عضو، دعوة بصفة %1$s، المحفظة |

A string test scans this block (between `<!-- Sharing (spec 013) -->` markers) and rejects
"invite link", "join request", "participant", "collaborator", "friend", "group", "household",
"family", "token", "account" used for a wallet, and Eastern Arabic digits.

## Maestro flows (`.maestro/013-sharing/`, each starting `# deferred: E2E on owner's device`)

| Flow | Scenario |
|---|---|
| `US1-invite-email.yaml` | owner (Plus) invites by email; pending row visible |
| `US1-invite-offline.part1/2/3.yaml` | offline invite waits, syncs |
| `US1-not-included.yaml` | Free owner sees "not included" |
| `US2-join.part1/2.yaml` | second account (via `api.js`) creates a link invitation; app opens `/invite?token=` with `openLink`; joins; wallet appears; partner transaction appears after refresh (brief checkpoint) |
| `US2-signed-out.yaml` | link opened signed out → sign in → invitation screen |
| `US2-unavailable.yaml` | used link → uniform message |
| `US3-share-link.yaml` | owner creates a link; share sheet opens |
| `US4-role-and-remove.yaml` | owner makes a member viewer, removes them |
| `US4-withdraw.yaml` | withdraw a pending invitation |
| `US5-leave.yaml` | member leaves; wallet gone |
| `US5-removed.part1/2.yaml` | owner removes via API; member's wallet disappears after refresh (brief checkpoint) |
| `US6-activity.yaml` | activity lists a join and a transaction |
| `US7-viewer-note.yaml` | viewer sees the note and no add button |

`api.js` gains `createInvitation`, `acceptInvitation`, `changeMemberRole`, `removeMember`.
