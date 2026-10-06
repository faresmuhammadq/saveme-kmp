# Quickstart: Push Notifications (client)

How to see spec 017 working. Owner policy: device steps are for the owner's phone; agents run the
host tests and the live contract QA only.

## 1. Host tests (no device, no Firebase)

```bash
./gradlew :shared:testAndroidHostTest --tests '*notifications*' --tests '*AppGraphTest*' --tests '*Layering*'
./gradlew :shared:verifyRoborazziAndroidHostTest
```

Expect: parser, router, registration, receiver, preferences, open-service, store and Robolectric
display/channel tests green; no `com.google.firebase` class on the test classpath.

## 2. Live contract QA (after backend spec 023 is committed)

Backend dev container up (`dev project open` in `../saveme-backend`), port 43871.

```bash
B=http://127.0.0.1:43871/api/v1
# sign in a throwaway account (see specs/002 quickstart for the sign-in body); keep TOKEN and SESSION
curl -s -X PUT "$B/me/devices/$SESSION" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H 'Accept: application/json' \
  -H 'X-Client-Platform: android' -H 'X-Client-Version: 1.0.0' -H "X-Correlation-Id: $(uuidgen)" \
  -d '{"push_token":"dev-fake-push-00000000-0000-7000-8000-000000000001"}'
curl -s "$B/me/notification-preferences" -H "Authorization: Bearer $TOKEN" …
```

Run the full list in contracts/push-api-client.md "Live QA"; capture the answers (tokens replaced
by fakes) into `CapturedNotificationsResponses.kt`.

## 3. Development build on a phone (owner)

1. `./gradlew :androidApp:assembleDevDebug`, install, sign in.
2. Account → Notifications: defaults (all on, 22:00–08:00, your profile time zone). Change, save.
3. Create your first budget → the explanation sheet → Turn on → allow.
4. Account → Developer tools → Simulate a notification → each type → Post → pull down the shade →
   tap → the screen of spec FR-031 opens. Post `budget_exceeded` twice → one notification.
5. Lock the phone (secure lock) → the notification shows without its text.
6. Sign out → the shade is empty.

## 4. Real Firebase delivery (owner, after the gated FCM phase)

Needs: dependency approval, Firebase project, `google-services.json` in `androidApp/src/<flavor>/`,
and the backend's `NOTIFICATIONS_PUSH_DRIVER=fcm` with the service-account key. Then: sign in on
the phone, invite its account by email from a second account → "You've been invited to a shared
wallet" arrives (app closed) → tap → the invitation notice opens (the brief's integration
checkpoint, read as backend spec 023 open question 2 does).
