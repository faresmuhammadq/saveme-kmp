# C1 · Auth completion (client)

**Pairs with**: backend [B1](../../../saveme-backend/specs/briefs/B01-identity-auth.md) → spec 007 (built) · **Call with**: `/speckit-specify specs/briefs/C01-auth-completion.md`

## Description

Finish the parts of client spec 001 that were never built: **registration** with a "check your
inbox" confirmation, **email verification** from the link (deep link or pasted link), **resend
verification** with its rate limit, the **verify prompt** wherever the server answers
`EMAIL_NOT_VERIFIED`, **forgot password** and **reset password** from the emailed link. These are
001 user stories 3 and 4 (tasks T082–T104); the E2E report marks all 12 of their scenarios
`not-built`.

## Consumes

`POST /auth/register`, `/auth/verify-email`, `/auth/resend-verification`,
`/auth/forgot-password`, `/auth/reset-password`; error codes for weak/equal-to-email passwords,
expired/used/superseded links, and the resend and reset rate limits.

## Can build before the backend ships

Nothing is blocked: B1 is built. Reuse 001's plan, contracts and `AuthLinkParser` design.

## Needs from the backend

Frozen already. Confirm the link format the mails carry (host, path, token parameter) against the
dev flavor's `linkHost` before building the deep-link intake.

## Considerations

- Registration answers the same whether or not the address exists (uniform answers); only a
  second registration of a verified address is refused.
- The dev backend logs mail (`MAIL_MAILER=log`). For E2E, add a way to read the latest link for an
  address (artisan or a dev-only endpoint, agreed with the backend) so US3/US4 scenarios leave
  `manual`.
- Google sign-in stays an extension point, out of scope.

## Integration checkpoint

`/speckit-e2e 001`: US3-S1…S7 and US4-S1…S5 automated and passing.
