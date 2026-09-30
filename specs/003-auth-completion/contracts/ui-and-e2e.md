# Contract: Screens, test tags and E2E seeds

## Test tags (FR-022, research R-08)

| Screen | Tags |
|---|---|
| Register | `register.email`, `register.password`, `register.displayName`, `register.submit`, `register.signIn` ("Already have an account?"), `register.alreadySignIn` and `register.resetPassword` (the already-registered actions) |
| CheckInbox | `checkInbox.goToSignIn` |
| VerifyEmail | `verifyEmail.continue`, `verifyEmail.resend`, `verifyEmail.signIn`, `verifyEmail.retry` |
| Home verify prompt | `home.verifyPrompt` (the alert; its "Resend email" action is an `InlineAlert` action with no modifier, so flows tap its text) |
| Email-not-verified sheet | `verifySheet.resend` |
| ForgotPassword | `forgotPassword.email`, `forgotPassword.submit` |
| ResetPassword | `resetPassword.password`, `resetPassword.submit`, `resetPassword.requestNewLink`, `resetPassword.continue` |
| PasteLink | `pasteLink.text`, `pasteLink.open` |

## E2E seeds (`run-e2e.sh`, research R-07)

| `# e2e-seed:` | Account | Also passes |
|---|---|---|
| `none` | — | — |
| `verified-user` | verified | `E2E_EMAIL`, `E2E_PASSWORD` |
| `unverified-user` | unverified | `E2E_EMAIL`, `E2E_PASSWORD` |
| `unverified-user+verify-link` | unverified | + `E2E_LINK` (fresh verification link) and `E2E_OLD_LINK` (superseded by `E2E_LINK`) |
| `verified-user+reset-link` | verified | + `E2E_LINK` (fresh reset link) and `E2E_OLD_LINK` (superseded) |

Links are issued with `LinkRepository::issue($userId, LinkKind::VerifyAddress|ResetPassword)` in
`artisan tinker` and built as `http://localhost:{port}/{verify-email|reset-password}?token=…`.
Flows open them with Maestro `openLink`.

## Copy (new keys, English and Arabic)

Register title and button, "Already have an account? Sign in", display-name hint,
already-registered notice with its two actions, check-inbox (verification and reset variants),
verify statuses (verifying, verified, continue), home verify prompt and "Verification email
sent", forgot and reset screens (titles, button, done, link invalid + request new link),
Paste link body and "Not a verification or reset link". Existing keys reused: `action_create_account`,
`sign_in_forgot_password`, `error_verification_link_invalid`, `error_reset_link_invalid`,
`error_password_reset_done`, `field_error_*`, `catalog_verify_alert` text (moved to a real key).
