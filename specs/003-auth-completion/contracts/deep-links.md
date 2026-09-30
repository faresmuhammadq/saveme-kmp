# Contract: Mail links into the app

Supersedes spec 001's `contracts/deep-links.md` where they differ (paths confirmed, port added,
manifest layout). Research R-03, R-04.

| Link | URI | Opens | Parses to |
|---|---|---|---|
| Verify email | `{scheme}://{linkHost}[:{linkPort}]/verify-email?token={secret}` | VerifyEmail | `AuthLink.VerifyEmail(token)` |
| Reset password | `{scheme}://{linkHost}[:{linkPort}]/reset-password?token={secret}` | ResetPassword | `AuthLink.ResetPassword(token)` |

Confirmed against the backend: `config/identity.php` `links.verify_url` / `links.reset_url`
followed by `?token=`.

## Per environment

| Flavor | Filter | Host / port | Verified |
|---|---|---|---|
| prod, staging | main manifest, `https` | `${linkHost}` | yes (`autoVerify`; backend serves `assetlinks.json`) |
| dev | dev manifest, `http` | `${linkHost}` + `${linkPort}` (e.g. `localhost:43871` via adb reverse) | no; Paste link screen as fallback |

## Rules

- `MainActivity` is `singleTask`; links arrive through `onCreate` and `onNewIntent` into
  `DeepLinkBus`; `AppNavHost` is the only consumer.
- Works signed in or signed out. On the auth stack the link screen replaces the top entry; on
  the signed-in stack it is pushed.
- The token lives in the target ViewModel only; never persisted, logged or shown.
- Rejected: other schemes, hosts, ports or paths; missing or empty token; token over 128 chars.

## Backend dependencies

1. Dev: `IDENTITY_VERIFY_URL` / `IDENTITY_RESET_URL` pointing at `http://localhost:43871/…` so
   mailed links open the dev app (proposed, research R-05; not needed for E2E, R-07).
2. Staging/prod: `assetlinks.json` with the release signing certificate.
