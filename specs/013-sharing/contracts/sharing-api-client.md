# Contract: Sharing API as the client uses it

**Source of truth**: `saveme-backend/docs/openapi/paths/sharing.yaml` v1.9, `paths/sync.yaml`
(`invitation` wallet-feed rows; own `wallet_member` rows on the user feed) and
`components/schemas.yaml` (`Problem.feature`), recorded at `saveme-backend@26e7c44`. Where this file
and the YAML differ, the YAML wins. Every request goes through the shared `ApiClient` (headers
P-02.5, correlation id P-13.1, 401/426 events, `FEATURE_NOT_ENTITLED` and `EMAIL_NOT_VERIFIED`
events). No call here is retried automatically except through the sync engine (A1, A2).

## Shared additions

- `ProblemDetails.feature: String?` (`@SerialName("feature")`).
- `ApiClient.callWithMeta<T>(block): ApiResult<Envelope<T>>` — like `call`, keeps `meta`.
- Request DTOs are `@Serializable` with exactly the members below and `explicitNulls` where noted;
  every DTO holding a token or a link overrides `toString` with `<redacted>`.

## A1 `PUT /wallets/{w}/invitations/{id}` — create (`InvitationApi.put`; sync engine, per record)

```json
{ "role": "editor", "invitee_email": "karim@example.com", "version": 0 }
{ "role": "viewer", "invitee_email": null, "version": 0 }
```

`invitee_email` is always serialised (explicit `null` for a link invitation).

| Answer | Client |
|---|---|
| 201 `{data: Invitation, meta: {share_url?}}` | store the copy; for a link, `onShareLink(id, share_url)` → `ShareLinks` |
| 200 replay `{data, meta: {}}` | store the copy; no link |
| 409 `VERSION_CONFLICT` (+`current` tombstone) | `acceptsClash` → take the tombstone (row removed), drop the change |
| 403 `EMAIL_NOT_VERIFIED` | refusal (Try again + Discard); app-wide verify prompt event |
| 403 `FEATURE_NOT_ENTITLED` (+`feature`) | refusal with `feature` (Try again + Discard); C3 refresh event |
| 403 `FORBIDDEN` | refusal "Only the owner can invite" (Discard) |
| 404 `NOT_FOUND` | refusal "No longer available" (Discard); clears the wallet's position (the next read decides) |
| 422 `VALIDATION_FAILED` (`/role`, `/invitee_email`, `IMMUTABLE_FIELD`), `INVALID_ID` | refusal with fields (Discard) |
| 429 `RATE_LIMITED` (+Retry-After) | engine waits and retries (Automatic class) — stays "Waiting to sync" |
| Offline / timeout / 5xx | engine retry per catalogue |

## A2 `DELETE /wallets/{w}/invitations/{id}` — withdraw (`InvitationApi.delete`; sync engine, per record)

Body `{ "version": 1 }`. 200 tombstone (also when already closed) → row removed. 409 (+`current`)
→ `acceptsClash` takes it. 403/404/422 → refusal (Discard restores the row). Offline/5xx → retry.

## A3 `POST /invitations/preview` (`InvitationApi.preview`)

Body `{ "token": "<43 chars>" }`. 200 `InvitationPreview {invitation_id, wallet_id, wallet_name,
role, invited_by_display_name (nullable), expires_at, already_member}` → `PreviewResult.Ready`.
404 → `Unavailable` (one copy for every cause). 422 → `InvalidLink`. 429 → `TooMany(retryAfterSeconds)`.
Offline/timeout → `Offline`. Else → `Failed(requestId)`.

## A4 `POST /invitations/accept` (`InvitationApi.accept`)

Body `{ "token": … }`. 200 `WalletMember` (new, already-member, or retry after a lost answer) →
`JoinResult.Joined(walletId)` after storing the row. 403 `FEATURE_NOT_ENTITLED` (any `feature`) →
`OwnerPlanFull`. 404 → `Unavailable`. 422 → `InvalidLink`. 429 → `TooMany`. Offline → `Offline`.
Else → `Failed(requestId)`.

## A5 `POST /invitations/decline` (`InvitationApi.decline`)

Body `{ "token": … }`. 204 → `Declined` (also a repeat). 404 → `Unavailable`. 422 → `InvalidLink`.
429 → `TooMany`. Offline → `Offline`. Else → `Failed`.

## A6 `PATCH /wallets/{w}/members/{uid}` — change role (`MemberApi.changeRole`)

Body `{ "role": "viewer", "version": 2 }`. 200 member → stored. 409 (+`current`) → store `current`,
`Changed`. 403 `FORBIDDEN` → `OnlyOwner`; `FEATURE_NOT_ENTITLED` → `NotEntitled(feature)`;
`EMAIL_NOT_VERIFIED` → `NotVerified`. 404 → `Gone` (re-read wallet). 422 `IMMUTABLE_FIELD /role` →
`OwnerRole`. Offline → `Offline`. Else → `Failed(requestId)`.

## A7 `DELETE /wallets/{w}/members/{uid}` — remove (`MemberApi.remove`)

Body `{ "version": 2 }`. 200 tombstone → row deleted (also already removed). 409 → `Changed`.
403 → `OnlyOwner` / `NotVerified`. 404 → `Gone`. Offline → `Offline`.

## A8 `POST /wallets/{w}/leave` (`MemberApi.leave`)

Body `{ "version": <own membership version> }`. 200 tombstone or 404 → `Left` (purge). 403 →
`OwnerCannotLeave`. 409 (+`current`) → store `current`, `Changed`. Offline → `Offline`. Else → `Failed`.

## A9 `GET /wallets/{w}/activity?limit=50[&cursor=…]` (`ActivityApi.page`)

`callPage<ActivityEntryDto>`: `{id, wallet_id, occurred_at, actor_user_id?, actor_display_name?,
action, subject_type, subject_id, role?}`. Unknown members ignored; unknown `action`/`subject_type`
skipped in domain. 400 `INVALID_CURSOR` → restart once from the first page. 404 → `Gone`. Offline →
`Offline`. Else → `Failed(requestId)`.

## A10 Feeds (spec 009, unchanged endpoints)

- Wallet feed row `type: invitation`, record = `Invitation` → `InvitationFeedApplier` (scope kind
  Wallet): version guard, tombstone removes unless a change waits, sweep after a complete read.
- User feed / wallet feed `wallet_member` rows: unchanged appliers; own live row also sets
  `wallet.my_role` (research R-12).

## Contract tests (MockEngine, examples from `sharing.yaml`)

`InvitationApiContractTest`, `MemberApiContractTest`, `ActivityApiContractTest`: request method,
path, body members exactly as above (token in body, never in URL; explicit null address), headers;
each answer row above decoded and mapped; `Problem.feature` read. Captured live answers
(`CapturedSharingResponses.kt`) drive the same through the real engine, handler and appliers.
