package com.fares.saveme.feature.notifications.data

/**
 * Answers captured from the real local backend on 2026-10-06 (spec 017 QA, qa-report.md "Live contract QA"; backend `e8c308a`
 * with the uncommitted spec 023 working tree, `notifications.yaml` v1.14), replayed by `NotificationsLiveReplayTest`.
 * Push addresses are the dev provider's `dev-fake-push-<uuid>` shape; no bearer credential is kept. Ids belong to throwaway
 * `qa017*` accounts. Bodies are verbatim, only the host in `type` URLs is as the server wrote it.
 */
internal object CapturedPushResponses {
    /** 200 for `PUT me/devices/01a11189-88f7-70c7-bddc-57b7e99c016b` */
    const val REGISTER_200 = """{"data":{"session_id":"01a11189-88f7-70c7-bddc-57b7e99c016b","registered_at":"2026-10-06T14:06:37.080322Z","refreshed_at":"2026-10-06T14:06:37.080322Z"},"meta":{}}"""

    /** 200 for `PUT me/devices/01a11189-88f7-70c7-bddc-57b7e99c016b` */
    const val REGISTER_200_AGAIN = """{"data":{"session_id":"01a11189-88f7-70c7-bddc-57b7e99c016b","registered_at":"2026-10-06T14:06:37.080322Z","refreshed_at":"2026-10-06T14:06:37.281112Z"},"meta":{}}"""

    /** 200 for `PUT me/devices/01a11189-88f7-70c7-bddc-57b7e99c016b` */
    const val REGISTER_200_ROTATED = """{"data":{"session_id":"01a11189-88f7-70c7-bddc-57b7e99c016b","registered_at":"2026-10-06T14:06:37.080322Z","refreshed_at":"2026-10-06T14:06:37.495256Z"},"meta":{}}"""

    /** 404 for `PUT me/devices/01a11189-8bc6-7079-87ba-bc8c8a440660` */
    const val REGISTER_404_OTHER_SESSION = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a11189-ac4f-7008-9e73-7e6c6dafd6e6"}"""

    /** 404 for `PUT me/devices/0192f7a0-9999-7000-8000-0000000000aa` */
    const val REGISTER_404_UNKNOWN_ID = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a11189-ad24-73b3-8b9f-336f128efde7"}"""

    /** 422 for `PUT me/devices/01a11189-88f7-70c7-bddc-57b7e99c016b` */
    const val REGISTER_422_UNKNOWN_FIELD = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a11189-adfe-7098-b0e2-2aac60fb3ac4","errors":[{"pointer":"\/platform","code":"UNKNOWN_FIELD","detail":"This field is not accepted here."}]}"""

    /** 422 for `PUT me/devices/01a11189-88f7-70c7-bddc-57b7e99c016b` */
    const val REGISTER_422_EMPTY_TOKEN = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a11189-aec2-73b2-9c35-be12229983a4","errors":[{"pointer":"\/push_token","code":"OUT_OF_RANGE","detail":"This value is too short, too long, or outside the allowed range."}]}"""

    /** 401 for `PUT me/devices/01a11189-88f7-70c7-bddc-57b7e99c016b` */
    const val REGISTER_401 = """{"type":"http:\/\/saveme-backend.test\/problems\/unauthenticated","title":"Unauthenticated","status":401,"detail":"Please sign in again.","code":"UNAUTHENTICATED","request_id":"01a11189-afa6-71ea-b197-062b265d4335"}"""

    /** 404 for `DELETE me/devices/01a11189-8bc6-7079-87ba-bc8c8a440660 ` */
    const val UNREGISTER_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a11189-ed6e-7279-805e-6378856198cf"}"""

    /** 401 for `DELETE me/devices/01a11189-88f7-70c7-bddc-57b7e99c016b ` */
    const val UNREGISTER_401 = """{"type":"http:\/\/saveme-backend.test\/problems\/unauthenticated","title":"Unauthenticated","status":401,"detail":"Please sign in again.","code":"UNAUTHENTICATED","request_id":"01a11189-ee55-72a5-a217-65ad5c275e38"}"""

    /** 200 for `GET me/notification-preferences ` */
    const val PREFERENCES_200_DEFAULTS = """{"data":{"version":1,"categories":{"recurring_due":true,"budget_exceeded":true,"sharing":true,"billing":true,"export_ready":true,"deletion_reminder":true},"quiet_hours":{"enabled":true,"start":"22:00","end":"08:00"},"updated_at":null},"meta":{}}"""

    /** 200 for `PUT me/notification-preferences` */
    const val PREFERENCES_PUT_200_SAVED = """{"data":{"version":2,"categories":{"recurring_due":true,"budget_exceeded":false,"sharing":true,"billing":true,"export_ready":true,"deletion_reminder":true},"quiet_hours":{"enabled":true,"start":"23:00","end":"07:00"},"updated_at":"2026-10-06T14:07:02.349734Z"},"meta":{}}"""

    /** 409 for `PUT me/notification-preferences` */
    const val PREFERENCES_PUT_409_CONFLICT = """{"type":"http:\/\/saveme-backend.test\/problems\/version-conflict","title":"Version conflict","status":409,"detail":"Someone changed this in the meantime. Review the latest version and try again.","code":"VERSION_CONFLICT","request_id":"01a1118a-0d46-709e-b1f4-1983e431e680","current":{"version":2,"categories":{"recurring_due":true,"budget_exceeded":false,"sharing":true,"billing":true,"export_ready":true,"deletion_reminder":true},"quiet_hours":{"enabled":true,"start":"23:00","end":"07:00"},"updated_at":"2026-10-06T14:07:02.349734Z"}}"""

    /** 422 for `PUT me/notification-preferences` */
    const val PREFERENCES_PUT_422_START_EQUALS_END = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a1118a-0e11-73c1-9b25-fa2411259135","errors":[{"pointer":"\/quiet_hours\/end","code":"OUT_OF_RANGE","detail":"This value is too short, too long, or outside the allowed range."}]}"""

    /** 422 for `PUT me/notification-preferences` */
    const val PREFERENCES_PUT_422_NOT_A_QUARTER_HOUR = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a1118a-0ee3-7111-9f01-74adf75d890b","errors":[{"pointer":"\/quiet_hours\/start","code":"OUT_OF_RANGE","detail":"This value is too short, too long, or outside the allowed range."}]}"""

    /** 422 for `PUT me/notification-preferences` */
    const val PREFERENCES_PUT_422_UNKNOWN_FIELD = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a1118a-0fad-719f-bf17-c87753d6b14e","errors":[{"pointer":"\/foo","code":"UNKNOWN_FIELD","detail":"This field is not accepted here."}]}"""

    /** 422 for `PUT me/notification-preferences` */
    const val PREFERENCES_PUT_422_MISSING_VERSION = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a1118a-107a-70c7-bc25-7e21267d78d6","errors":[{"pointer":"\/version","code":"REQUIRED","detail":"This field is required."}]}"""

    /** 200 for `GET me/notification-preferences ` */
    const val PREFERENCES_200_AFTER_SAVE = """{"data":{"version":2,"categories":{"recurring_due":true,"budget_exceeded":false,"sharing":true,"billing":true,"export_ready":true,"deletion_reminder":true},"quiet_hours":{"enabled":true,"start":"23:00","end":"07:00"},"updated_at":"2026-10-06T14:07:02.349734Z"},"meta":{}}"""

    /** 401 for `GET me/notification-preferences ` */
    const val PREFERENCES_401 = """{"type":"http:\/\/saveme-backend.test\/problems\/unauthenticated","title":"Unauthenticated","status":401,"detail":"Please sign in again.","code":"UNAUTHENTICATED","request_id":"01a1118a-128c-7385-bdec-9b01a79a39f9"}"""

    /** 429 for `PUT me/devices/01a11189-88f7-70c7-bddc-57b7e99c016b` */
    const val REGISTER_429 = """{"type":"http:\/\/saveme-backend.test\/problems\/rate-limited","title":"Rate limited","status":429,"detail":"Too many attempts. Please wait a moment and try again.","code":"RATE_LIMITED","request_id":"01a1118a-395c-730c-be3f-9402d64ddc7d","retry_after_seconds":3564}"""

    /** 429 for `PUT me/notification-preferences` */
    const val PREFERENCES_PUT_429 = """{"type":"http:\/\/saveme-backend.test\/problems\/rate-limited","title":"Rate limited","status":429,"detail":"Too many attempts. Please wait a moment and try again.","code":"RATE_LIMITED","request_id":"01a1118a-60ba-70f8-aaff-9cb9de51846a","retry_after_seconds":3579}"""

    /** 429 for `DELETE me/devices/01a11189-88f7-70c7-bddc-57b7e99c016b ` */
    const val UNREGISTER_429 = """{"type":"http:\/\/saveme-backend.test\/problems\/rate-limited","title":"Rate limited","status":429,"detail":"Too many attempts. Please wait a moment and try again.","code":"RATE_LIMITED","request_id":"01a1118a-4f8e-731c-bf3b-1d628df0bccc","retry_after_seconds":3558}"""

    /** `Retry-After` of the 429 answers (seconds) as the server sent them. */
    const val REGISTER_429_RETRY_AFTER = "3564"
    const val UNREGISTER_429_RETRY_AFTER = "3558"
    const val PREFERENCES_429_RETRY_AFTER = "3579"

    /**
     * The `data` block of each push the backend's `log` gateway recorded for the throwaway users (`PayloadBuilder::build`:
     * `schema`, `type`, `category`, `notification_id` plus the type's identifiers), with the collapse key the server stored.
     * Seven of the twelve types could be produced on the dev server; `wallet_ownership_received`, `subscription_*`,
     * `occurrences_due_summary` and `occurrence_auto_confirm_refused` were not (qa-report.md).
     */
    data class CapturedPush(val data: Map<String, String>, val serverCollapseKey: String)

    const val WALLET_ID = "01a1118d-3f38-7540-95f7-40c2527572fd"
    const val EXPORT_ID = "01a1118b-2b9e-7259-98f3-860ee4f74e52"
    const val INVITATION_ID = "01a1118d-403c-7228-bdd7-1680e1cfb8ca"
    const val OCCURRENCE_ID = "01a1118e-1b88-711c-ae87-ef56b9446354"
    const val BUDGET_ID = "01a1118f-3ffa-7ecf-b7e5-faac452c9eb1"

    val EXPORT_READY = CapturedPush(
        mapOf("schema" to "1", "type" to "export_ready", "category" to "export_ready", "notification_id" to "01a1118c-0cbb-7319-bd44-81d767a991a2", "export_id" to EXPORT_ID),
        "export:$EXPORT_ID",
    )
    val INVITATION_RECEIVED = CapturedPush(
        mapOf("schema" to "1", "type" to "invitation_received", "category" to "sharing", "notification_id" to "01a1118d-5dd4-725f-aa54-c70cc9824d7d", "invitation_id" to INVITATION_ID),
        "invitation:$INVITATION_ID",
    )
    val MEMBER_ROLE_CHANGED = CapturedPush(
        mapOf("schema" to "1", "type" to "member_role_changed", "category" to "sharing", "notification_id" to "01a1118d-8471-735d-b153-78140035d714", "wallet_id" to WALLET_ID, "role" to "viewer"),
        "wallet:$WALLET_ID",
    )
    val MEMBER_REMOVED = CapturedPush(
        mapOf("schema" to "1", "type" to "member_removed", "category" to "sharing", "notification_id" to "01a1118d-f122-7058-85ae-d1d1886939e8", "wallet_id" to WALLET_ID),
        "wallet:$WALLET_ID",
    )
    val OCCURRENCE_DUE = CapturedPush(
        mapOf("schema" to "1", "type" to "occurrence_due", "category" to "recurring_due", "notification_id" to "01a1118e-1ee4-71ea-a5e0-64613d2c3a09", "wallet_id" to WALLET_ID, "occurrence_id" to OCCURRENCE_ID),
        "occurrence:$OCCURRENCE_ID",
    )
    val BUDGET_EXCEEDED = CapturedPush(
        mapOf("schema" to "1", "type" to "budget_exceeded", "category" to "budget_exceeded", "notification_id" to "01a1118f-43fb-708a-ab9f-820d5ccce17f", "budget_id" to BUDGET_ID),
        "budget:$BUDGET_ID",
    )
    val DELETION_REMINDER = CapturedPush(
        mapOf("schema" to "1", "type" to "deletion_reminder", "category" to "deletion_reminder", "notification_id" to "01a1118f-7a03-7362-a5d5-c625977a7eac"),
        "deletion",
    )

    val ALL_PUSHES = listOf(EXPORT_READY, INVITATION_RECEIVED, MEMBER_ROLE_CHANGED, MEMBER_REMOVED, OCCURRENCE_DUE, BUDGET_EXCEEDED, DELETION_REMINDER)
}
