# Research: Auth Completion (Register, Verify, Reset)

Phase 0 of the plan. Spec 001 already researched most of this feature (its R-03, R-04, R-11,
R-12 and its tasks T082–T104); each decision below either adopts that work or records what
changes, with the reason.

## R-01 Reuse spec 001's design for US3/US4

- **Decision**: Build from 001's tasks T082–T104, contracts `identity-api-client.md` and
  `deep-links.md`, and data-model `AuthLink`, with the deltas in R-02 to R-08.
- **Rationale**: The design was reviewed and the surrounding code already anticipates it:
  `EntryEffect.NavigateRegister`, `SignInEffect.NavigateForgotPassword/NavigateRegister`,
  `DevToolsEffect.NavigatePasteLink`, `DeepLinkBus`, `AuthField.DisplayName/Token`,
  `ClientValidation.validateNewPassword/validateDisplayName`, `FieldProblem`, the
  `VERIFICATION_TOKEN_INVALID`/`RESET_TOKEN_INVALID` codes and their copy all exist unused.
- **Alternatives**: A fresh design. Rejected: it would duplicate reviewed decisions and diverge
  from placeholders already in `AppNavHost`.

## R-02 Registration of an existing address

- **Decision**: Add `EMAIL_ALREADY_REGISTERED` to `ApiErrorCode` (retry class `Never`) and a
  `RegisterOutcome.AlreadyRegistered`; the register screen shows an inline notice with "Sign in"
  and "Reset password", both carrying the typed email.
- **Rationale**: The contract answers 409 for an existing address (identity.yaml
  `registerAccount`), recorded as the one exception to uniform answers in the backend security
  baseline; constitution v1.2.0 P-09.4 now mirrors it. 001's test "202 identical for new and
  existing addresses" (T084) no longer describes the server and is replaced.
- **Alternatives**: Show "check your inbox" on 409 to stay uniform. Rejected by the owner
  (2026-09-29): no mail arrives, so the person waits for nothing.

## R-03 Link format and parsing

- **Decision**: Links are `{scheme}://{linkHost}[:{linkPort}]/verify-email?token=…` and
  `/reset-password?token=…`. `AuthLinkParser(linkHost, linkPort, scheme)` accepts exactly those,
  a token of 1–128 characters, whitespace-trimmed for pasted text. `AppConfig` gains `linkPort`
  (dev only; staging and prod use the default port).
- **Rationale**: Confirmed against the backend: `config/identity.php` `links.verify_url` /
  `reset_url` + `?token=` (mail classes append `'?token='.$secret`); this closes 001 R-12's
  "to confirm" note and the `TODO(link-paths)` in T088. Dev reaches the backend through
  `adb reverse` on `localhost:43871` (spec 001/E2E runner), so the dev link host is `localhost`
  with port `43871`, which the host alone cannot express.
- **Alternatives**: Match on path only, ignoring host. Rejected: any site could then open the
  app's reset screen with a crafted secret, and a staging link would open a dev build.

## R-04 Deep-link intake on Android

- **Decision**:
  - `MainActivity` gets `android:launchMode="singleTask"`, and forwards `intent.dataString`
    from `onCreate` (when not restored) and `onNewIntent` to `DeepLinkBus.submit`.
  - `AppNavHost` collects `DeepLinkBus.links`, parses them, and pushes `VerifyEmailKey(token)` or
    `ResetPasswordKey(token)`: on the auth stack it replaces the top entry, on the signed-in
    stack it pushes.
  - Manifests:
    - The main manifest holds the `https` filter (`autoVerify="true"`, host `${linkHost}`, both
      paths); the https filter can't be satisfied on dev, which is harmless.
    - The dev manifest adds an `http` filter with `${linkHost}` and `${linkPort}` (not verified).
- **Rationale**: 001 R-12 and T094/T095, adjusted to the repository as it is: there are no
  `src/staging` or `src/prod` manifest directories, and one main filter using the placeholder
  serves both.
- **Alternatives**: Per-flavor manifest directories for staging and prod. Rejected: two
  identical files that only differ by a placeholder the build already fills.

## R-05 Dev link host from the backend

- **Decision**: Don't depend on the backend's dev link configuration. Tapping a mailed link in dev
  works once the backend sets `IDENTITY_VERIFY_URL=http://localhost:43871/verify-email` and
  `IDENTITY_RESET_URL=http://localhost:43871/reset-password` (proposed to the backend owner);
  until then the dev Paste link screen accepts a link and a `localhost` rewrite is not attempted.
- **Rationale**: The backend default is `http://saveme-backend.test/…`, unreachable from a device.
  The Paste link screen (FR-008) already covers manual testing; E2E uses R-07.
- **Alternatives**: Have the parser accept `saveme-backend.test` in dev and rewrite it. Rejected:
  special-casing one developer's hostname in app code.

## R-06 Resend cooldown

- **Decision**: After "Resend email" the action stays disabled while the request runs and for
  30 seconds after success (spec FR-013). A `RATE_LIMITED` answer shows the wait in minutes from
  `retry_after_seconds`.
- **Rationale**: The server allows 3 per hour; the cooldown only prevents accidental double sends.
  001 T092 said 60 s; the spec (FR-013) sets 30 s, and the spec wins.
- **Alternatives**: Keep the button disabled for the full server window. Rejected: the client
  doesn't know it until the server answers.

## R-07 E2E: links without reading mail

- **Decision**: `run-e2e.sh` gains two seed kinds:
  - `unverified-user+verify-link`
  - `verified-user+reset-link`

  After creating the account, the runner issues a real link through the backend's
  `LinkRepository::issue($userId, LinkKind::…)` in `artisan tinker`, which returns the secret, and
  passes `E2E_LINK` (built with the app's dev link host) to Maestro. Flows open it with `openLink`,
  exercising the real deep-link intake. Expired and used links are made by issuing a second link
  (supersedes the first) or using one twice.
- **Rationale**: Dev mail goes to the log (`MAIL_MAILER=log`) and Maestro can't run shell
  commands mid-flow. Issuing through the backend's own port gives the same secret a mail would
  carry, with no log parsing and no dev-only endpoint. This settles spec §9's second open
  question on the client side.
- **Alternatives**: Parse `storage/logs/laravel.log`. Rejected: brittle, and the flow can't read
  it mid-run. A dev-only backend endpoint. Rejected: backend work for a test-only need.

## R-08 Test tags and screenshots

- **Decision**: Every new interactive element gets a `testTag` of the form `<screen>.<element>`
  (FR-022). Auth screens get a screenshot test class mirroring
  `AccountScreensScreenshotTest` (EN LTR, AR RTL, EN at 2× font).
- **Rationale**: The E2E run on 2026-09-29 showed text selectors break on duplicated labels
  ("Sign in" title and button) and on components whose semantics hide text (`AppSelectField`).
- **Alternatives**: Text selectors only. Rejected for the reasons above.

## Carried over unchanged from spec 001

- **R-03 (001)**: branch on the problem `code`; unknown codes fall back to `UNKNOWN`.
- **R-04 (001)**: no automatic retry of any auth POST. Offline and timeout show "try again".
- **R-11 (001)**: three flavors; the link host comes from the build.
