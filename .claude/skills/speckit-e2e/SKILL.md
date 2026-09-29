---
name: "speckit-e2e"
description: "Generate Maestro E2E flows from the current feature's acceptance scenarios, run them on an Android emulator against the local saveme-backend, and report pass/fail per scenario. Run on demand, or as the optional after_implement hook."
compatibility: "Requires Maestro, adb with one device/emulator, the saveme-backend dev container, and spec-kit project structure"
---

# E2E acceptance scenarios

Every spec lists numbered **Acceptance Scenarios** (Given/When/Then) under each user story. This
command makes each one an executable Maestro flow, runs the flows against the real app and the
local backend, and records the result next to the spec.

## User Input

```text
$ARGUMENTS
```

An argument selects the feature: a directory name (`002-account-security`) or its number (`002`).
Without one, use `feature_directory` from `.specify/feature.json`. `--run-only` skips generation
(steps 2–4) and only runs the existing flows.

## Steps

1. **Resolve the feature.** `FEATURE` = the directory name under `specs/`. Read
   `specs/$FEATURE/spec.md`. Also read `plan.md` if it exists, for the screens and routes involved.

2. **List the scenarios.** For each `### User Story N` section, number its acceptance scenarios
   in order. The ID is `US<N>-S<M>` (for example `US2-S3`). Keep the full Given/When/Then text.
   Every scenario gets an ID, including the ones you will not automate.

3. **Classify each scenario.** A scenario is *automatable* when one device and the UI can drive it
   to its Then. Otherwise it is *manual*, with one of these reasons:
   - `multi-device`: needs a second signed-in device or session at the same time.
   - `time`: waits for real time to pass (token expiry, lockout windows).
   - `mail`: needs a link or code from an email (the backend's `MAIL_MAILER=log`).
   - `not-built`: the screen or behaviour is not in the app yet.
   - `external`: needs something outside the app and backend (store, OS setting you can't script).
   
   Never fake a manual scenario (for example by skipping its Given) to make it automatable.

4. **Write the flows.** One file per automatable scenario:
   `.maestro/$FEATURE/US<N>-S<M>-<short-kebab-slug>.yaml`. Update an existing flow for the same ID
   rather than adding a second one. Delete the flows of scenarios that no longer exist in the spec.
   Template:

   ```yaml
   # US2-S3: Given <…>, When <…>, Then <…>   (full text from the spec, one line)
   # e2e-seed: verified-user
   appId: com.fares.saveme
   tags:
     - US2
   ---
   - runFlow: ../common/sign-in.yaml
   - tapOn: "Change password"
   # …When steps…
   - assertVisible: "…Then text…"
   ```

   Rules:
   - `# e2e-seed:` states the account the flow needs: `none`, `verified-user` or
     `unverified-user`. `run-e2e.sh` creates a fresh one per flow and passes it as `${E2E_EMAIL}` /
     `${E2E_PASSWORD}`. Never hard-code an account.
   - Start from `../common/launch-clean.yaml` (signed out) or `../common/sign-in.yaml` (on Home).
     When several flows repeat the same steps, move them into a new subflow in `.maestro/common/`
     tagged `subflow`.
   - **Selectors:** use `id:` (a Compose `testTag`) first. Otherwise use the exact English text
     from `shared/src/commonMain/composeResources/values/strings.xml`, and only when it is unique on
     the screen. Never use coordinates, indexes or `point:`.
   - When an element has no selector you can use, add `Modifier.testTag("<screen>.<element>")` in
     its composable (camelCase screen, for example `signIn.submit`), passing it through the
     component's `modifier` parameter. That is the only production-code change this command may
     make. Tags are exposed to Maestro in dev builds by `MainActivity`.
   - Every **Then** becomes at least one assertion (`assertVisible`, `assertNotVisible`, or a
     text check after `extendedWaitUntil`). A flow without an assertion for its Then is incomplete.
   - Wait for network results with `extendedWaitUntil` (timeout ≤ 20000), not `waitForAnimationToEnd`
     or fixed sleeps.

5. **Run.** From the repository root run `.specify/scripts/bash/run-e2e.sh $FEATURE`. Add
   `--skip-install` if the current dev build is already installed, or `--flow <name>` to rerun one
   flow.
   - Exit 2 (preflight): show the message and stop. Do not write a report. The user has to install
     Maestro, start an emulator, or start the backend container.
   - Exit 0 or 1: go on.

6. **Diagnose each FAIL.** Read `build/e2e/$FEATURE/<flow>.log` and the screenshots in
   `build/e2e/$FEATURE/debug/<flow>/`. Decide which side is wrong:
   - **Flow defect** (wrong selector, missing wait, wrong copy): fix the flow and rerun only that
     flow with `--flow`. Two attempts at most, then report it as failing.
   - **App defect** (the app does not do what the scenario says): do not change the flow, and
     never weaken or remove an assertion to get a pass. Report it with the step that failed and what
     the screen showed.
   - **Environment** (backend error 5xx, seeding failed): report it as such.

7. **Write `specs/$FEATURE/e2e-report.md`**, overwriting the previous one:

   ```markdown
   # E2E report: <feature>

   **Run**: <date> · device <serial> · saveme-kmp@<short sha> · saveme-backend@<short sha>
   **Result**: <passed> passed · <failed> failed · <manual> manual · <total> scenarios

   | Scenario | Flow | Result | Notes |
   |---|---|---|---|
   | US1-S1 | — | manual | multi-device: needs device B signed in |
   | US2-S1 | US2-S1-correct-current-password.yaml | pass | |
   | US2-S2 | US2-S2-wrong-current-password.yaml | FAIL (app) | step 6 assertVisible "Current password is wrong" — screen showed generic error |
   ```

   Every scenario ID from step 2 appears exactly once. The Result for a failure names the side:
   `FAIL (app)`, `FAIL (flow)` or `FAIL (env)`.

8. **Summarise for the user:** the Result line, then one line per failing scenario (ID, side,
   cause), then the manual scenarios grouped by reason. For each app defect, name the likely code
   location if you found it while diagnosing.
