# Contract: Mail links into the app

Clarified 2026-09-26: link host = API base URL host; dev uses LAN IP over HTTP.

| Link | URI (per environment host) | Opens | Parses to |
|---|---|---|---|
| Verify email | `{scheme}://{linkHost}/verify-email?token={secret}` | VerifyEmail screen | `AuthLink.VerifyEmail(token)` |
| Reset password | `{scheme}://{linkHost}/reset-password?token={secret}` | ResetPassword screen | `AuthLink.ResetPassword(token)` |

`{scheme}` = `https` (staging, prod) · `http` (dev). Paths and the `token` parameter name are **to be confirmed** against the backend mail templates (research R-12); nothing else depends on them.

## Per environment

| Flavor | Intent filter | Verified? | Fallback |
|---|---|---|---|
| prod, staging | `https`, host `${linkHost}`, paths above | yes (`autoVerify`, backend serves `/.well-known/assetlinks.json` with the release signing cert SHA-256) | — |
| dev | `http`, host `${linkHost}` (IP:port), paths above | no — chooser or browser | Dev tools → **Paste link** screen feeding the same parser |

## Rules

- Works signed in or signed out; a link never requires the current session to belong to the link's account.
- The token is held only in the target screen's state; never persisted, never logged.
- Parser rejects: other hosts, other paths, missing/empty token, token longer than 128 characters.
- Paste link screen and the `http` filter exist in the `dev` flavor only (FR-027b).
- Opening a link while a form is mid-edit replaces the top of the auth stack; the signed-in stack gets the screen pushed on top.

## Backend dependencies

1. Mail templates build links from the same host as the API base URL, with the paths above.
2. `assetlinks.json` served on staging/prod hosts (package `com.fares.saveme`, release cert fingerprint).
