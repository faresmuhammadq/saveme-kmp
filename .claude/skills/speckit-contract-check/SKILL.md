---
name: "speckit-contract-check"
description: "Check the backend error catalogue and API conventions for changes since the last spec, before writing a new spec. Runs automatically as a before_specify hook."
compatibility: "Requires ../saveme-backend (or SAVEME_BACKEND_DIR) and spec-kit project structure"
---

# Backend contract check

The backend repository (`saveme-backend`) owns the error codes and API conventions this client
must follow (constitution, "External contract first"). Every new spec starts by finding out what
changed there since the last spec was written.

## Steps

1. Run `.specify/scripts/bash/check-backend-contract.sh` from the repository root.
   - If it exits non-zero, show the error and stop: the new spec must not be written against an
     unknown contract. Ask the user where the backend repository is (`SAVEME_BACKEND_DIR`).
2. Read the full report, and for each API conventions diff hunk, read the surrounding section of
   `docs/api-conventions.md` in the backend when the hunk alone is not enough to judge it.
3. Summarise for the user, in this order:
   - **Error codes**: added, removed, and changed codes (status, level, retryable, deprecated).
     A REMOVED code breaks the catalogue's own rule — flag it as a backend defect.
   - **Client mismatches**: any `NOT IN CATALOGUE`, `RETRY MISMATCH` or `DEPRECATED` line means
     `ApiErrorCode.kt` is already wrong, whatever the new feature is.
   - **Conventions**: each changed rule in one line, and whether the client already honours it
     (check the code under `shared/src/commonMain/kotlin/com/fares/saveme/core/network/`).
   - If nothing changed: say so in one line.
4. Carry the result into the spec being written:
   - Add the `**Backend contract**` header line exactly as the report prints it (under
     `**Input**`). The next spec's check uses it as its baseline.
   - Under section 5 (API), add a `Contract changes since last spec` subsection listing every
     change that affects the client, each with the requirement (FR) that handles it — or, if it
     is outside this feature, the note "follow-up: not in scope of this spec". Omit the
     subsection when nothing changed.
   - Client mismatches are never out of scope: add a functional requirement to fix them.
