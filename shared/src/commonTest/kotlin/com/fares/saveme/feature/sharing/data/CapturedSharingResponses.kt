package com.fares.saveme.feature.sharing.data

/**
 * Responses captured from the live local backend (`saveme-backend@26e7c44`, sharing.yaml v1.9, spec 013 host QA, qa-report.md
 * "Live contract QA", 2026-10-05) for requests sent with the app's headers and the exact bodies the sharing clients build.
 * Bodies are verbatim, with ONE edit: every invitation secret (the token of a link, in `meta.share_url` and in request bodies) is
 * replaced by a fixed fake of the same 43-character shape ([TOKEN_MAIL] and the others), so no real secret is stored (P-09.2).
 * `*_REQUEST` is the body sent. Request ids are the live ones.
 *
 * Accounts (throwaway): owner `USER_OWNER` (Plus, later lapsed and re-granted), editor `USER_EDITOR` (Free), `USER_UNVERIFIED`
 * (not verified, member), plus Free and rate-limit accounts. Wallet `WALLET` "Home" (EGP).
 */
@Suppress("MaxLineLength")
object CapturedSharingResponses {
    const val USER_OWNER = "01a10b53-ac75-712c-8a11-67a063746cd2"
    const val USER_EDITOR = "01a10b53-c4a2-72c5-9b9e-bc0382bd3f06"
    const val USER_UNVERIFIED = "01a10b53-c73c-7068-b82f-d2676e793bb9"
    const val WALLET = "01a10b54-4f1d-7c39-9958-05e80dbc6c59"
    const val INVITATION_EMAIL = "01a10b54-5094-7ada-a1d8-222ac080f39d"
    const val INVITATION_LINK = "01a10b54-522e-782e-8ab3-ff26abf3a14d"
    const val INVITATION_LINK_TWO = "01a10b54-e161-78e7-b545-35ab486cb90c"
    const val INVITATION_LINK_THREE = "01a10b56-c2fb-715c-9888-8f6b1db445b4"
    const val INVITATION_LINK_FOUR = "01a10b58-0022-7d36-abae-15adf60ac79f"
    const val INVITATION_PENDING = "01a10b55-84c5-736b-b406-912a66fc081b"
    const val INVITATION_REPLACEMENT = "01a10b55-8696-7deb-afd7-6c681051a37e"
    const val INVITATION_OWN_ADDRESS = "01a10b55-37ad-7382-a68f-7827ca7e9ab9"
    const val EMAIL_MEMBER_ROW = "01a10b54-dd8d-7293-8fa2-875f997a831a"
    const val TOKEN_MAIL = "Fake03TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"
    const val TOKEN_LINK = "Fake01TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"
    const val TOKEN_LINK_TWO = "Fake04TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"
    const val TOKEN_LINK_THREE = "Fake05TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"
    const val TOKEN_LINK_FOUR = "Fake08TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"
    const val TOKEN_UNKNOWN = "Fake02TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"

    /** 201: PUT wallets/<id>/invitations/<id> (PUT create email invitation as editor (201, meta {})) */
    const val PUT_EMAIL_CREATED_201 = """{"data":{"id":"01a10b54-5094-7ada-a1d8-222ac080f39d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":4,"created_at":"2026-10-05T09:10:37.606530Z","updated_at":"2026-10-05T09:10:37.606530Z","deleted_at":null,"role":"editor","channel":"email","invitee_email":"qa13+10051210-ed@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:10:37.598412Z"},"meta":{}}"""

    /** the body sent */
    const val PUT_EMAIL_CREATED_201_REQUEST = """{"role":"editor","invitee_email":"qa13+10051210-ed@example.com","version":0}"""

    /** 200: PUT wallets/<id>/invitations/<id> (PUT identical replay (200)) */
    const val PUT_EMAIL_REPLAY_200 = """{"data":{"id":"01a10b54-5094-7ada-a1d8-222ac080f39d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":4,"created_at":"2026-10-05T09:10:37.606530Z","updated_at":"2026-10-05T09:10:37.606530Z","deleted_at":null,"role":"editor","channel":"email","invitee_email":"qa13+10051210-ed@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:10:37.598412Z"},"meta":{}}"""

    /** the body sent */
    const val PUT_EMAIL_REPLAY_200_REQUEST = """{"role":"editor","invitee_email":"qa13+10051210-ed@example.com","version":0}"""

    /** 409: PUT wallets/<id>/invitations/<id> (PUT same id, other role, version 0 against the live record (409 current)) */
    const val PUT_REPLAY_OTHER_ROLE_VERSION_ZERO_409 = """{"type":"http:\/\/saveme-backend.test\/problems\/version-conflict","title":"Version conflict","status":409,"detail":"Someone changed this in the meantime. Review the latest version and try again.","code":"VERSION_CONFLICT","request_id":"01a10b54-51f0-70cb-ac49-3c963dd41270","current":{"id":"01a10b54-5094-7ada-a1d8-222ac080f39d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":4,"created_at":"2026-10-05T09:10:37.606530Z","updated_at":"2026-10-05T09:10:37.606530Z","deleted_at":null,"role":"editor","channel":"email","invitee_email":"qa13+10051210-ed@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:10:37.598412Z"}}"""

    /** the body sent */
    const val PUT_REPLAY_OTHER_ROLE_VERSION_ZERO_409_REQUEST = """{"role":"viewer","invitee_email":"qa13+10051210-ed@example.com","version":0}"""

    /** 201: PUT wallets/<id>/invitations/<id> (PUT create link invitation as viewer (201, meta.share_url once)) */
    const val PUT_LINK_CREATED_201 = """{"data":{"id":"01a10b54-522e-782e-8ab3-ff26abf3a14d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":6,"created_at":"2026-10-05T09:10:37.996434Z","updated_at":"2026-10-05T09:10:37.996434Z","deleted_at":null,"role":"viewer","channel":"link","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:10:37.992977Z"},"meta":{"share_url":"http:\/\/saveme-backend.test\/invite?token=Fake01TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}}"""

    /** the body sent */
    const val PUT_LINK_CREATED_201_REQUEST = """{"role":"viewer","invitee_email":null,"version":0}"""

    /** 200: PUT wallets/<id>/invitations/<id> (PUT link replay (200, meta {} and no link)) */
    const val PUT_LINK_REPLAY_200 = """{"data":{"id":"01a10b54-522e-782e-8ab3-ff26abf3a14d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":6,"created_at":"2026-10-05T09:10:37.996434Z","updated_at":"2026-10-05T09:10:37.996434Z","deleted_at":null,"role":"viewer","channel":"link","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:10:37.992977Z"},"meta":{}}"""

    /** the body sent */
    const val PUT_LINK_REPLAY_200_REQUEST = """{"role":"viewer","invitee_email":null,"version":0}"""

    /** 403: PUT wallets/<id>/invitations/<id> (PUT by a Free owner (403 FEATURE_NOT_ENTITLED, feature sharing.enabled)) */
    const val PUT_FREE_OWNER_403 = """{"type":"http:\/\/saveme-backend.test\/problems\/feature-not-entitled","title":"Feature not entitled","status":403,"detail":"Your plan does not include this.","code":"FEATURE_NOT_ENTITLED","request_id":"01a10b55-34c4-72c2-82ce-a458556e305c","feature":"sharing.enabled"}"""

    /** the body sent */
    const val PUT_FREE_OWNER_403_REQUEST = """{"role":"viewer","invitee_email":"someone@example.com","version":0}"""

    /** 403: PUT wallets/<id>/invitations/<id> (PUT by an editor (403 FORBIDDEN)) */
    const val PUT_EDITOR_FORBIDDEN_403 = """{"type":"http:\/\/saveme-backend.test\/problems\/forbidden","title":"Forbidden","status":403,"detail":"Your role in this wallet does not allow this.","code":"FORBIDDEN","request_id":"01a10b55-35c6-705e-bd0f-e0349c21402d"}"""

    /** the body sent */
    const val PUT_EDITOR_FORBIDDEN_403_REQUEST = """{"role":"viewer","invitee_email":"x@example.com","version":0}"""

    /** 403: PUT wallets/<id>/invitations/<id> (PUT by an owner whose address is not verified (403 EMAIL_NOT_VERIFIED)) */
    const val PUT_UNVERIFIED_OWNER_403 = """{"type":"http:\/\/saveme-backend.test\/problems\/email-not-verified","title":"Address not verified","status":403,"detail":"Verify your email address first. We can send the link again.","code":"EMAIL_NOT_VERIFIED","request_id":"01a10b58-079a-72ab-8f55-dc8ef3250494"}"""

    /** the body sent */
    const val PUT_UNVERIFIED_OWNER_403_REQUEST = """{"role":"viewer","invitee_email":"x@example.com","version":0}"""

    /** 422: PUT wallets/<id>/invitations/<id> (PUT role "owner") */
    const val PUT_INVALID_ROLE_422 = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10b55-3686-73e0-a06a-943770bc2dea","errors":[{"pointer":"\/role","code":"INVALID_ENUM_VALUE","detail":"This value is not one of the allowed options."}]}"""

    /** the body sent */
    const val PUT_INVALID_ROLE_422_REQUEST = """{"role":"owner","invitee_email":"x@example.com","version":0}"""

    /** 422: PUT wallets/<id>/invitations/<id> (PUT malformed address) */
    const val PUT_INVALID_EMAIL_422 = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10b55-36db-70d5-8f7a-c1e4c28158f2","errors":[{"pointer":"\/invitee_email","code":"INVALID_TYPE","detail":"This value is not in the expected form."}]}"""

    /** the body sent */
    const val PUT_INVALID_EMAIL_422_REQUEST = """{"role":"viewer","invitee_email":"not-an-email","version":0}"""

    /** 422: PUT wallets/<id>/invitations/<id> (PUT address of 262 characters) */
    const val PUT_EMAIL_TOO_LONG_422 = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10b55-3731-70e0-8107-94893e9506f7","errors":[{"pointer":"\/invitee_email","code":"OUT_OF_RANGE","detail":"This value is too short, too long, or outside the allowed range."}]}"""

    /** the body sent */
    const val PUT_EMAIL_TOO_LONG_422_REQUEST = """{"role":"viewer","invitee_email":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa@example.com","version":0}"""

    /** 422: PUT wallets/<id>/invitations/not-a-uuid (PUT with a malformed invitation id (INVALID_ID)) */
    const val PUT_MALFORMED_ID_422 = """{"type":"http:\/\/saveme-backend.test\/problems\/invalid-id","title":"Invalid identifier","status":422,"detail":"The identifier is not valid.","code":"INVALID_ID","request_id":"01a10b55-3787-714b-943e-d04e339d9b7b"}"""

    /** the body sent */
    const val PUT_MALFORMED_ID_422_REQUEST = """{"role":"viewer","invitee_email":"x@example.com","version":0}"""

    /** 201: PUT wallets/<id>/invitations/<id> (PUT to the owner's own address: the server does NOT refuse (201); the client rule does) */
    const val PUT_OWN_ADDRESS_CREATED_201 = """{"data":{"id":"01a10b55-37ad-7382-a68f-7827ca7e9ab9","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":18,"created_at":"2026-10-05T09:11:36.760278Z","updated_at":"2026-10-05T09:11:36.760278Z","deleted_at":null,"role":"viewer","channel":"email","invitee_email":"qa13+10051209-own@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:11:36.752644Z"},"meta":{}}"""

    /** the body sent */
    const val PUT_OWN_ADDRESS_CREATED_201_REQUEST = """{"role":"viewer","invitee_email":"qa13+10051209-own@example.com","version":0}"""

    /** 201: PUT wallets/<id>/invitations/<id> (PUT to the address of an existing member: the server does NOT refuse (201)) */
    const val PUT_MEMBER_ADDRESS_CREATED_201 = """{"data":{"id":"01a10b55-3858-7707-82b6-d4aed7de4fec","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":20,"created_at":"2026-10-05T09:11:36.928769Z","updated_at":"2026-10-05T09:11:36.928769Z","deleted_at":null,"role":"viewer","channel":"email","invitee_email":"qa13+10051210-ed@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:11:36.921315Z"},"meta":{}}"""

    /** the body sent */
    const val PUT_MEMBER_ADDRESS_CREATED_201_REQUEST = """{"role":"viewer","invitee_email":"qa13+10051210-ed@example.com","version":0}"""

    /** 403: PUT wallets/<id>/invitations/<id> (PUT when owner + member + pending invitations fill the Plus quota of 5 (403, feature sharing.max_members_per_wallet)) */
    const val PUT_OVER_CAP_FIRST_403 = """{"type":"http:\/\/saveme-backend.test\/problems\/feature-not-entitled","title":"Feature not entitled","status":403,"detail":"Your plan does not include this.","code":"FEATURE_NOT_ENTITLED","request_id":"01a10b55-39db-720e-9f8a-eb71d9a38a43","feature":"sharing.max_members_per_wallet"}"""

    /** the body sent */
    const val PUT_OVER_CAP_FIRST_403_REQUEST = """{"role":"viewer","invitee_email":"pending-a@example.com","version":0}"""

    /** 403: PUT wallets/<id>/invitations/<id> (PUT beyond the Plus cap of 5 people incl. pending (403, feature sharing.max_members_per_wallet)) */
    const val PUT_OVER_CAP_403 = """{"type":"http:\/\/saveme-backend.test\/problems\/feature-not-entitled","title":"Feature not entitled","status":403,"detail":"Your plan does not include this.","code":"FEATURE_NOT_ENTITLED","request_id":"01a10b55-888b-71f6-9100-87dacf138d33","feature":"sharing.max_members_per_wallet"}"""

    /** the body sent */
    const val PUT_OVER_CAP_403_REQUEST = """{"role":"viewer","invitee_email":"pending-c@example.com","version":0}"""

    /** 403: PUT wallets/<id>/invitations/<id> (PUT link invitation beyond the cap (403, sharing.max_members_per_wallet)) */
    const val PUT_OVER_CAP_LINK_403 = """{"type":"http:\/\/saveme-backend.test\/problems\/feature-not-entitled","title":"Feature not entitled","status":403,"detail":"Your plan does not include this.","code":"FEATURE_NOT_ENTITLED","request_id":"01a10b55-3dcd-7029-b2e9-f88bb0dc1143","feature":"sharing.max_members_per_wallet"}"""

    /** the body sent */
    const val PUT_OVER_CAP_LINK_403_REQUEST = """{"role":"viewer","invitee_email":null,"version":0}"""

    /** 201: PUT wallets/<id>/invitations/<id> (PUT create pending invitation (201)) */
    const val PUT_PENDING_CREATED_201 = """{"data":{"id":"01a10b55-84c5-736b-b406-912a66fc081b","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":26,"created_at":"2026-10-05T09:11:56.482356Z","updated_at":"2026-10-05T09:11:56.482356Z","deleted_at":null,"role":"viewer","channel":"email","invitee_email":"pending-a@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:11:56.475176Z"},"meta":{}}"""

    /** the body sent */
    const val PUT_PENDING_CREATED_201_REQUEST = """{"role":"viewer","invitee_email":"pending-a@example.com","version":0}"""

    /** 422: PUT wallets/<id>/invitations/<id> (PUT same id at version 1 with another role (422 IMMUTABLE_FIELD /role)) */
    const val PUT_IMMUTABLE_ROLE_422 = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10b55-8588-7097-ae2c-ee9574fde6d2","errors":[{"pointer":"\/role","code":"IMMUTABLE_FIELD","detail":"This field cannot be changed."}]}"""

    /** the body sent */
    const val PUT_IMMUTABLE_ROLE_422_REQUEST = """{"role":"editor","invitee_email":"pending-a@example.com","version":1}"""

    /** 422: PUT wallets/<id>/invitations/<id> (PUT same id at version 1 with another address (422 IMMUTABLE_FIELD /invitee_email)) */
    const val PUT_IMMUTABLE_ADDRESS_422 = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10b55-85ef-7387-89e3-5f0199e5a782","errors":[{"pointer":"\/invitee_email","code":"IMMUTABLE_FIELD","detail":"This field cannot be changed."}]}"""

    /** the body sent */
    const val PUT_IMMUTABLE_ADDRESS_422_REQUEST = """{"role":"viewer","invitee_email":"other@example.com","version":1}"""

    /** 200: PUT wallets/<id>/invitations/<id> (PUT same id, same body at version 1 (200)) */
    const val PUT_SAME_BODY_VERSION_ONE_200 = """{"data":{"id":"01a10b55-84c5-736b-b406-912a66fc081b","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":26,"created_at":"2026-10-05T09:11:56.482356Z","updated_at":"2026-10-05T09:11:56.482356Z","deleted_at":null,"role":"viewer","channel":"email","invitee_email":"pending-a@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:11:56.475176Z"},"meta":{}}"""

    /** the body sent */
    const val PUT_SAME_BODY_VERSION_ONE_200_REQUEST = """{"role":"viewer","invitee_email":"pending-a@example.com","version":1}"""

    /** 201: PUT wallets/<id>/invitations/<id> (PUT a new invitation to the same address (201; the earlier one becomes revoked)) */
    const val PUT_REINVITE_SAME_ADDRESS_201 = """{"data":{"id":"01a10b55-8696-7deb-afd7-6c681051a37e","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":29,"created_at":"2026-10-05T09:11:56.961843Z","updated_at":"2026-10-05T09:11:56.961843Z","deleted_at":null,"role":"editor","channel":"email","invitee_email":"pending-a@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:11:56.950290Z"},"meta":{}}"""

    /** the body sent */
    const val PUT_REINVITE_SAME_ADDRESS_201_REQUEST = """{"role":"editor","invitee_email":"Pending-A@Example.com","version":0}"""

    /** 200: GET wallets/<id>/invitations/<id> (GET the replaced invitation (tombstone, status revoked)) */
    const val GET_REPLACED_INVITATION_200 = """{"data":{"id":"01a10b55-84c5-736b-b406-912a66fc081b","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":28,"created_at":"2026-10-05T09:11:56.482356Z","updated_at":"2026-10-05T09:11:56.950290Z","deleted_at":"2026-10-05T09:11:56.950290Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:56.475176Z"},"meta":{}}"""

    /** 429: PUT wallets/<id>/invitations/<id> (PUT the 21st invitation in 24 hours (429, retry_after_seconds 86394)) */
    const val PUT_RATE_LIMITED_429 = """{"type":"http:\/\/saveme-backend.test\/problems\/rate-limited","title":"Rate limited","status":429,"detail":"Too many attempts. Please wait a moment and try again.","code":"RATE_LIMITED","request_id":"01a10b57-baca-7002-9fb8-affc9383f37b","retry_after_seconds":86394}"""

    /** the body sent */
    const val PUT_RATE_LIMITED_429_REQUEST = """{"role":"viewer","invitee_email":"rate21@example.com","version":0}"""

    /** 404: PUT wallets/<id>/invitations/<id> (PUT by a non-member) */
    const val PUT_NON_MEMBER_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b55-417a-727e-8401-c375ab0fa61c"}"""

    /** the body sent */
    const val PUT_NON_MEMBER_404_REQUEST = """{"role":"viewer","invitee_email":"x@example.com","version":0}"""

    /** 200: DELETE wallets/<id>/invitations/<id> (DELETE withdraw {version:1} (200 tombstone, status revoked)) */
    const val DELETE_WITHDRAW_200 = """{"data":{"id":"01a10b55-37ad-7382-a68f-7827ca7e9ab9","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":22,"created_at":"2026-10-05T09:11:36.760278Z","updated_at":"2026-10-05T09:11:55.856094Z","deleted_at":"2026-10-05T09:11:55.856094Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:36.752644Z"},"meta":{}}"""

    /** the body sent */
    const val DELETE_WITHDRAW_200_REQUEST = """{"version":1}"""

    /** 200: DELETE wallets/<id>/invitations/<id> (DELETE withdraw again (200 same tombstone)) */
    const val DELETE_WITHDRAW_AGAIN_200 = """{"data":{"id":"01a10b55-37ad-7382-a68f-7827ca7e9ab9","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":22,"created_at":"2026-10-05T09:11:36.760278Z","updated_at":"2026-10-05T09:11:55.856094Z","deleted_at":"2026-10-05T09:11:55.856094Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:36.752644Z"},"meta":{}}"""

    /** the body sent */
    const val DELETE_WITHDRAW_AGAIN_200_REQUEST = """{"version":1}"""

    /** 409: DELETE wallets/<id>/invitations/<id> (DELETE withdraw with a stale version 5 on a live invitation (409 current)) */
    const val DELETE_STALE_VERSION_409 = """{"type":"http:\/\/saveme-backend.test\/problems\/version-conflict","title":"Version conflict","status":409,"detail":"Someone changed this in the meantime. Review the latest version and try again.","code":"VERSION_CONFLICT","request_id":"01a10b55-836d-70af-9a9d-2167136ec060","current":{"id":"01a10b55-3858-7707-82b6-d4aed7de4fec","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":20,"created_at":"2026-10-05T09:11:36.928769Z","updated_at":"2026-10-05T09:11:36.928769Z","deleted_at":null,"role":"viewer","channel":"email","invitee_email":"qa13+10051210-ed@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:11:36.921315Z"}}"""

    /** the body sent */
    const val DELETE_STALE_VERSION_409_REQUEST = """{"version":5}"""

    /** 403: DELETE wallets/<id>/invitations/<id> (DELETE withdraw by an editor (403 FORBIDDEN)) */
    const val DELETE_BY_EDITOR_403 = """{"type":"http:\/\/saveme-backend.test\/problems\/forbidden","title":"Forbidden","status":403,"detail":"Your role in this wallet does not allow this.","code":"FORBIDDEN","request_id":"01a10b55-83d1-70d5-9d6c-ff2d589e6f2b"}"""

    /** the body sent */
    const val DELETE_BY_EDITOR_403_REQUEST = """{"version":1}"""

    /** 200: DELETE wallets/<id>/invitations/<id> (DELETE withdraw of an expired (swept) invitation (200 tombstone, status expired)) */
    const val DELETE_ALREADY_EXPIRED_200 = """{"data":{"id":"01a10b55-8696-7deb-afd7-6c681051a37e","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":59,"created_at":"2026-10-05T09:11:56.961843Z","updated_at":"2026-10-05T09:14:03.111078Z","deleted_at":"2026-10-05T09:14:03.111078Z","role":"editor","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"expired","expires_at":"2026-10-05T08:14:00.000000Z"},"meta":{}}"""

    /** the body sent */
    const val DELETE_ALREADY_EXPIRED_200_REQUEST = """{"version":1}"""

    /** 200: GET wallets/<id>/invitations?limit=50 (GET invitations list (not used by the client)) */
    const val GET_LIST_200 = """{"data":[{"id":"01a10b54-5094-7ada-a1d8-222ac080f39d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":4,"created_at":"2026-10-05T09:10:37.606530Z","updated_at":"2026-10-05T09:10:37.606530Z","deleted_at":null,"role":"editor","channel":"email","invitee_email":"qa13+10051210-ed@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:10:37.598412Z"},{"id":"01a10b54-522e-782e-8ab3-ff26abf3a14d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":6,"created_at":"2026-10-05T09:10:37.996434Z","updated_at":"2026-10-05T09:10:37.996434Z","deleted_at":null,"role":"viewer","channel":"link","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:10:37.992977Z"}],"meta":{"next_cursor":null,"has_more":false,"limit":50}}"""

    /** 200: GET wallets/<id>/invitations/<id> (GET one invitation (not used by the client)) */
    const val GET_ONE_200 = """{"data":{"id":"01a10b54-5094-7ada-a1d8-222ac080f39d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":4,"created_at":"2026-10-05T09:10:37.606530Z","updated_at":"2026-10-05T09:10:37.606530Z","deleted_at":null,"role":"editor","channel":"email","invitee_email":"qa13+10051210-ed@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:10:37.598412Z"},"meta":{}}"""

    /** 200: GET wallets/<id>/invitations/<id> (GET an invitation past expires_at before the sweep (still status pending)) */
    const val GET_AGED_STILL_PENDING_200 = """{"data":{"id":"01a10b55-8696-7deb-afd7-6c681051a37e","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":29,"created_at":"2026-10-05T09:11:56.961843Z","updated_at":"2026-10-05T09:14:00.960705Z","deleted_at":null,"role":"editor","channel":"email","invitee_email":"pending-a@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-05T08:14:00.000000Z"},"meta":{}}"""

    /** 200: GET wallets/<id>/invitations/<id> (GET an email invitation after sharing:expire-invitations (status expired)) */
    const val GET_EXPIRED_TOMBSTONE_200 = """{"data":{"id":"01a10b55-8696-7deb-afd7-6c681051a37e","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":59,"created_at":"2026-10-05T09:11:56.961843Z","updated_at":"2026-10-05T09:14:03.111078Z","deleted_at":"2026-10-05T09:14:03.111078Z","role":"editor","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"expired","expires_at":"2026-10-05T08:14:00.000000Z"},"meta":{}}"""

    /** 404: POST invitations/preview (POST preview with an unknown token) */
    const val PREVIEW_UNKNOWN_TOKEN_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b54-daeb-7194-a428-0d6c79505430"}"""

    /** the body sent */
    const val PREVIEW_UNKNOWN_TOKEN_404_REQUEST = """{"token":"Fake02TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 422: POST invitations/preview (POST preview with a malformed token (INVALID_TYPE /token)) */
    const val PREVIEW_MALFORMED_TOKEN_422 = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10b54-db5b-70e9-a888-bf161eba12bd","errors":[{"pointer":"\/token","code":"INVALID_TYPE","detail":"This value is not in the expected form."}]}"""

    /** the body sent */
    const val PREVIEW_MALFORMED_TOKEN_422_REQUEST = """{"token":"short"}"""

    /** 422: POST invitations/preview (POST preview with no token (REQUIRED /token)) */
    const val PREVIEW_EMPTY_BODY_422 = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10b54-dbaa-71dc-b0ce-ece6b7a9f222","errors":[{"pointer":"\/token","code":"REQUIRED","detail":"This field is required."}]}"""

    /** the body sent */
    const val PREVIEW_EMPTY_BODY_422_REQUEST = """{}"""

    /** 404: POST invitations/preview (POST preview of an email invitation signed in as another address) */
    const val PREVIEW_OTHER_ADDRESS_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b54-dbf6-72af-9464-4540fa114ecd"}"""

    /** the body sent */
    const val PREVIEW_OTHER_ADDRESS_404_REQUEST = """{"token":"Fake03TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 404: POST invitations/accept (POST accept of an email invitation signed in as another address) */
    const val ACCEPT_OTHER_ADDRESS_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b54-dc61-704f-9773-0890e7e36225"}"""

    /** the body sent */
    const val ACCEPT_OTHER_ADDRESS_404_REQUEST = """{"token":"Fake03TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 200: POST invitations/preview (POST preview of the emailed token (200)) */
    const val PREVIEW_EMAIL_200 = """{"data":{"invitation_id":"01a10b54-5094-7ada-a1d8-222ac080f39d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","wallet_name":"Home","role":"editor","invited_by_display_name":"OWN QA","expires_at":"2026-10-12T09:10:37.598412Z","already_member":false},"meta":{}}"""

    /** the body sent */
    const val PREVIEW_EMAIL_200_REQUEST = """{"token":"Fake03TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 200: POST invitations/accept (POST accept (200 member)) */
    const val ACCEPT_EMAIL_200 = """{"data":{"id":"01a10b54-dd8d-7293-8fa2-875f997a831a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":8,"created_at":"2026-10-05T09:11:13.549218Z","updated_at":"2026-10-05T09:11:13.549218Z","deleted_at":null,"member_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","role":"editor"},"meta":{}}"""

    /** the body sent */
    const val ACCEPT_EMAIL_200_REQUEST = """{"token":"Fake03TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 200: POST invitations/accept (POST accept again by the same person (200 same member)) */
    const val ACCEPT_REPEAT_200 = """{"data":{"id":"01a10b54-dd8d-7293-8fa2-875f997a831a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":8,"created_at":"2026-10-05T09:11:13.549218Z","updated_at":"2026-10-05T09:11:13.549218Z","deleted_at":null,"member_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","role":"editor"},"meta":{}}"""

    /** the body sent */
    const val ACCEPT_REPEAT_200_REQUEST = """{"token":"Fake03TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 404: POST invitations/preview (POST preview of a used token) */
    const val PREVIEW_AFTER_ACCEPT_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b54-de62-7329-86e3-58a6e5f91ac6"}"""

    /** the body sent */
    const val PREVIEW_AFTER_ACCEPT_404_REQUEST = """{"token":"Fake03TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 200: POST invitations/preview (POST preview of a pending link by the wallet owner (already_member true)) */
    const val PREVIEW_ALREADY_MEMBER_200 = """{"data":{"invitation_id":"01a10b54-522e-782e-8ab3-ff26abf3a14d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","wallet_name":"Home","role":"viewer","invited_by_display_name":"OWN QA","expires_at":"2026-10-12T09:10:37.992977Z","already_member":true},"meta":{}}"""

    /** the body sent */
    const val PREVIEW_ALREADY_MEMBER_200_REQUEST = """{"token":"Fake01TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 200: POST invitations/preview (POST preview by an unverified person (200)) */
    const val PREVIEW_UNVERIFIED_200 = """{"data":{"invitation_id":"01a10b54-522e-782e-8ab3-ff26abf3a14d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","wallet_name":"Home","role":"viewer","invited_by_display_name":"OWN QA","expires_at":"2026-10-12T09:10:37.992977Z","already_member":false},"meta":{}}"""

    /** the body sent */
    const val PREVIEW_UNVERIFIED_200_REQUEST = """{"token":"Fake01TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 200: POST invitations/accept (POST accept by an unverified person (200)) */
    const val ACCEPT_UNVERIFIED_200 = """{"data":{"id":"01a10b54-e078-7098-becb-f1c55198e003","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":11,"created_at":"2026-10-05T09:11:14.296960Z","updated_at":"2026-10-05T09:11:14.296960Z","deleted_at":null,"member_user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","role":"viewer"},"meta":{}}"""

    /** the body sent */
    const val ACCEPT_UNVERIFIED_200_REQUEST = """{"token":"Fake01TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 404: POST invitations/accept (POST accept of a used link) */
    const val ACCEPT_USED_LINK_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b54-e121-71a0-b49c-1de7d44cb9e3"}"""

    /** the body sent */
    const val ACCEPT_USED_LINK_404_REQUEST = """{"token":"Fake01TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 204: POST invitations/decline (POST decline (204)) */
    const val DECLINE_204 = """"""

    /** the body sent */
    const val DECLINE_204_REQUEST = """{"token":"Fake04TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 204: POST invitations/decline (POST decline again (204)) */
    const val DECLINE_REPEAT_204 = """"""

    /** the body sent */
    const val DECLINE_REPEAT_204_REQUEST = """{"token":"Fake04TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 404: POST invitations/accept (POST accept after decline) */
    const val ACCEPT_AFTER_DECLINE_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b54-e360-73ed-b0c3-5d5a44ec0785"}"""

    /** the body sent */
    const val ACCEPT_AFTER_DECLINE_404_REQUEST = """{"token":"Fake04TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 404: POST invitations/decline (POST decline unknown token) */
    const val DECLINE_UNKNOWN_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b54-e3c9-72fb-9cbf-4cd599da619a"}"""

    /** the body sent */
    const val DECLINE_UNKNOWN_404_REQUEST = """{"token":"Fake02TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 422: POST invitations/decline (POST decline malformed token) */
    const val DECLINE_MALFORMED_422 = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10b54-e429-73c0-b008-ce277b41d1ad","errors":[{"pointer":"\/token","code":"INVALID_TYPE","detail":"This value is not in the expected form."}]}"""

    /** the body sent */
    const val DECLINE_MALFORMED_422_REQUEST = """{"token":"x"}"""

    /** 403: POST invitations/accept (POST accept while the owner is on Free (403 FEATURE_NOT_ENTITLED, sharing.enabled)) */
    const val ACCEPT_OWNER_PLAN_FREE_403 = """{"type":"http:\/\/saveme-backend.test\/problems\/feature-not-entitled","title":"Feature not entitled","status":403,"detail":"Your plan does not include this.","code":"FEATURE_NOT_ENTITLED","request_id":"01a10b57-2249-7216-8411-e337930bbf4e","feature":"sharing.enabled"}"""

    /** the body sent */
    const val ACCEPT_OWNER_PLAN_FREE_403_REQUEST = """{"token":"Fake05TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 200: POST invitations/preview (POST preview while the owner is on Free (still 200)) */
    const val PREVIEW_OWNER_PLAN_FREE_200 = """{"data":{"invitation_id":"01a10b56-c2fb-715c-9888-8f6b1db445b4","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","wallet_name":"Home","role":"viewer","invited_by_display_name":"OWN QA","expires_at":"2026-10-12T09:13:17.953067Z","already_member":false},"meta":{}}"""

    /** the body sent */
    const val PREVIEW_OWNER_PLAN_FREE_200_REQUEST = """{"token":"Fake05TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 404: POST invitations/preview (POST preview of an expired invitation) */
    const val PREVIEW_EXPIRED_LINK_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b57-6e2f-728a-aafe-3c27a80fac4d"}"""

    /** the body sent */
    const val PREVIEW_EXPIRED_LINK_404_REQUEST = """{"token":"Fake05TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 404: POST invitations/accept (POST accept of an invitation of a deleted wallet) */
    const val ACCEPT_DELETED_WALLET_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b58-0a08-7105-bce3-869383533fa7"}"""

    /** the body sent */
    const val ACCEPT_DELETED_WALLET_404_REQUEST = """{"token":"Fake06TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 429: POST invitations/preview (POST preview, 31st token call in the hour (429, retry_after_seconds 3597)) */
    const val TOKEN_RATE_LIMITED_429 = """{"type":"http:\/\/saveme-backend.test\/problems\/rate-limited","title":"Rate limited","status":429,"detail":"Too many attempts. Please wait a moment and try again.","code":"RATE_LIMITED","request_id":"01a10b57-c71f-736f-a705-3ff5f3435b69","retry_after_seconds":3597}"""

    /** the body sent */
    const val TOKEN_RATE_LIMITED_429_REQUEST = """{"token":"Fake07TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 429: POST invitations/accept (POST accept while limited (429)) */
    const val ACCEPT_RATE_LIMITED_429 = """{"type":"http:\/\/saveme-backend.test\/problems\/rate-limited","title":"Rate limited","status":429,"detail":"Too many attempts. Please wait a moment and try again.","code":"RATE_LIMITED","request_id":"01a10b57-c7d3-703f-8357-5eb9a2a3cf54","retry_after_seconds":3597}"""

    /** the body sent */
    const val ACCEPT_RATE_LIMITED_429_REQUEST = """{"token":"Fake07TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 429: POST invitations/decline (POST decline while limited (429)) */
    const val DECLINE_RATE_LIMITED_429 = """{"type":"http:\/\/saveme-backend.test\/problems\/rate-limited","title":"Rate limited","status":429,"detail":"Too many attempts. Please wait a moment and try again.","code":"RATE_LIMITED","request_id":"01a10b57-c833-7233-b972-b7ecd8d50305","retry_after_seconds":3597}"""

    /** the body sent */
    const val DECLINE_RATE_LIMITED_429_REQUEST = """{"token":"Fake07TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 200: POST invitations/accept (POST accept by a removed member (200, a NEW member row id)) */
    const val ACCEPT_REJOIN_200 = """{"data":{"id":"01a10b58-014a-709c-8433-cbd6e29d179a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":64,"created_at":"2026-10-05T09:14:39.306467Z","updated_at":"2026-10-05T09:14:39.306467Z","deleted_at":null,"member_user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","role":"viewer"},"meta":{}}"""

    /** the body sent */
    const val ACCEPT_REJOIN_200_REQUEST = """{"token":"Fake08TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"}"""

    /** 200: PATCH wallets/<id>/members/<id> (PATCH role editor to viewer {role, version:1} (200 v2)) */
    const val PATCH_ROLE_VIEWER_200 = """{"data":{"id":"01a10b54-dd8d-7293-8fa2-875f997a831a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":37,"created_at":"2026-10-05T09:11:13.549218Z","updated_at":"2026-10-05T09:12:13.091598Z","deleted_at":null,"member_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","role":"viewer"},"meta":{}}"""

    /** the body sent */
    const val PATCH_ROLE_VIEWER_200_REQUEST = """{"role":"viewer","version":1}"""

    /** 409: PATCH wallets/<id>/members/<id> (PATCH role with a stale version (409 current)) */
    const val PATCH_ROLE_STALE_409 = """{"type":"http:\/\/saveme-backend.test\/problems\/version-conflict","title":"Version conflict","status":409,"detail":"Someone changed this in the meantime. Review the latest version and try again.","code":"VERSION_CONFLICT","request_id":"01a10b55-c679-7014-b876-f4f9eaab47e9","current":{"id":"01a10b54-dd8d-7293-8fa2-875f997a831a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":37,"created_at":"2026-10-05T09:11:13.549218Z","updated_at":"2026-10-05T09:12:13.091598Z","deleted_at":null,"member_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","role":"viewer"}}"""

    /** the body sent */
    const val PATCH_ROLE_STALE_409_REQUEST = """{"role":"editor","version":1}"""

    /** 200: PATCH wallets/<id>/members/<id> (PATCH role viewer to editor on a Plus owner (200 v3)) */
    const val PATCH_ROLE_EDITOR_200 = """{"data":{"id":"01a10b54-dd8d-7293-8fa2-875f997a831a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":3,"change_seq":39,"created_at":"2026-10-05T09:11:13.549218Z","updated_at":"2026-10-05T09:12:13.354486Z","deleted_at":null,"member_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","role":"editor"},"meta":{}}"""

    /** the body sent */
    const val PATCH_ROLE_EDITOR_200_REQUEST = """{"role":"editor","version":2}"""

    /** 422: PATCH wallets/<id>/members/<id> (PATCH the owner's own row (422 IMMUTABLE_FIELD /role)) */
    const val PATCH_OWNER_ROW_422 = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10b55-c7d8-71ba-876e-59bb0bf39ed8","errors":[{"pointer":"\/role","code":"IMMUTABLE_FIELD","detail":"This field cannot be changed."}]}"""

    /** the body sent */
    const val PATCH_OWNER_ROW_422_REQUEST = """{"role":"viewer","version":1}"""

    /** 422: PATCH wallets/<id>/members/<id> (PATCH a member to "owner" (422 IMMUTABLE_FIELD /role)) */
    const val PATCH_TO_OWNER_422 = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10b55-c83c-7053-9f18-a3796bc0ea27","errors":[{"pointer":"\/role","code":"IMMUTABLE_FIELD","detail":"This field cannot be changed."}]}"""

    /** the body sent */
    const val PATCH_TO_OWNER_422_REQUEST = """{"role":"owner","version":3}"""

    /** 422: PATCH wallets/<id>/members/<id> (PATCH role "admin" (422 INVALID_ENUM_VALUE /role)) */
    const val PATCH_INVALID_ENUM_422 = """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10b55-c89b-73e2-8335-72cd0c79396c","errors":[{"pointer":"\/role","code":"INVALID_ENUM_VALUE","detail":"This value is not one of the allowed options."}]}"""

    /** the body sent */
    const val PATCH_INVALID_ENUM_422_REQUEST = """{"role":"admin","version":3}"""

    /** 403: PATCH wallets/<id>/members/<id> (PATCH by an editor (403 FORBIDDEN)) */
    const val PATCH_BY_EDITOR_403 = """{"type":"http:\/\/saveme-backend.test\/problems\/forbidden","title":"Forbidden","status":403,"detail":"Your role in this wallet does not allow this.","code":"FORBIDDEN","request_id":"01a10b55-c8ef-73c3-8ea8-8403f1996fec"}"""

    /** the body sent */
    const val PATCH_BY_EDITOR_403_REQUEST = """{"role":"editor","version":1}"""

    /** 404: PATCH wallets/<id>/members/<id> (PATCH a user who is not a member) */
    const val PATCH_UNKNOWN_MEMBER_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b55-c9bc-738e-afcd-9a80b2d9f038"}"""

    /** the body sent */
    const val PATCH_UNKNOWN_MEMBER_404_REQUEST = """{"role":"viewer","version":1}"""

    /** 404: PATCH wallets/<id>/members/<id> (PATCH a removed member) */
    const val PATCH_REMOVED_MEMBER_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b55-cca6-72e2-a336-0d04eb0d4cfd"}"""

    /** the body sent */
    const val PATCH_REMOVED_MEMBER_404_REQUEST = """{"role":"viewer","version":2}"""

    /** 403: PATCH wallets/<id>/members/<id> (PATCH raise on a Free owner (403 FEATURE_NOT_ENTITLED, sharing.enabled)) */
    const val PATCH_FREE_OWNER_RAISE_403 = """{"type":"http:\/\/saveme-backend.test\/problems\/feature-not-entitled","title":"Feature not entitled","status":403,"detail":"Your plan does not include this.","code":"FEATURE_NOT_ENTITLED","request_id":"01a10b57-215f-70ae-91d9-d5da7dc42fc5","feature":"sharing.enabled"}"""

    /** the body sent */
    const val PATCH_FREE_OWNER_RAISE_403_REQUEST = """{"role":"editor","version":4}"""

    /** 200: PATCH wallets/<id>/members/<id> (PATCH lower to viewer on a Free owner (200, never plan-checked)) */
    const val PATCH_FREE_OWNER_LOWER_200 = """{"data":{"id":"01a10b54-dd8d-7293-8fa2-875f997a831a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":4,"change_seq":49,"created_at":"2026-10-05T09:11:13.549218Z","updated_at":"2026-10-05T09:13:20.732905Z","deleted_at":null,"member_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","role":"viewer"},"meta":{}}"""

    /** the body sent */
    const val PATCH_FREE_OWNER_LOWER_200_REQUEST = """{"role":"viewer","version":4}"""

    /** 200: DELETE wallets/<id>/members/<id> (DELETE member {version:1} (200 tombstone)) */
    const val DELETE_MEMBER_200 = """{"data":{"id":"01a10b54-e078-7098-becb-f1c55198e003","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":41,"created_at":"2026-10-05T09:11:14.296960Z","updated_at":"2026-10-05T09:12:14.582464Z","deleted_at":"2026-10-05T09:12:14.582464Z","member_user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","role":"viewer"},"meta":{}}"""

    /** the body sent */
    const val DELETE_MEMBER_200_REQUEST = """{"version":1}"""

    /** 200: DELETE wallets/<id>/members/<id> (DELETE member again (200 same tombstone)) */
    const val DELETE_MEMBER_AGAIN_200 = """{"data":{"id":"01a10b54-e078-7098-becb-f1c55198e003","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":41,"created_at":"2026-10-05T09:11:14.296960Z","updated_at":"2026-10-05T09:12:14.582464Z","deleted_at":"2026-10-05T09:12:14.582464Z","member_user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","role":"viewer"},"meta":{}}"""

    /** the body sent */
    const val DELETE_MEMBER_AGAIN_200_REQUEST = """{"version":1}"""

    /** 409: DELETE wallets/<id>/members/<id> (DELETE member with a stale version (409 current)) */
    const val DELETE_MEMBER_STALE_409 = """{"type":"http:\/\/saveme-backend.test\/problems\/version-conflict","title":"Version conflict","status":409,"detail":"Someone changed this in the meantime. Review the latest version and try again.","code":"VERSION_CONFLICT","request_id":"01a10b55-cae3-725b-9bf6-b8264560ec6e","current":{"id":"01a10b54-e078-7098-becb-f1c55198e003","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":11,"created_at":"2026-10-05T09:11:14.296960Z","updated_at":"2026-10-05T09:11:14.296960Z","deleted_at":null,"member_user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","role":"viewer"}}"""

    /** the body sent */
    const val DELETE_MEMBER_STALE_409_REQUEST = """{"version":9}"""

    /** 403: DELETE wallets/<id>/members/<id> (DELETE member by an editor (403 FORBIDDEN)) */
    const val DELETE_MEMBER_BY_EDITOR_403 = """{"type":"http:\/\/saveme-backend.test\/problems\/forbidden","title":"Forbidden","status":403,"detail":"Your role in this wallet does not allow this.","code":"FORBIDDEN","request_id":"01a10b55-ca82-7386-811d-3139eedc6ca9"}"""

    /** the body sent */
    const val DELETE_MEMBER_BY_EDITOR_403_REQUEST = """{"version":1}"""

    /** 403: DELETE wallets/<id>/members/<id> (DELETE the owner's own row (403 FORBIDDEN)) */
    const val DELETE_OWNER_ROW_403 = """{"type":"http:\/\/saveme-backend.test\/problems\/forbidden","title":"Forbidden","status":403,"detail":"Your role in this wallet does not allow this.","code":"FORBIDDEN","request_id":"01a10b55-cb55-7368-9139-aa9e1b1bc93a"}"""

    /** the body sent */
    const val DELETE_OWNER_ROW_403_REQUEST = """{"version":1}"""

    /** 200: POST wallets/<id>/leave (POST leave {version:4} (200 own tombstone)) */
    const val LEAVE_200 = """{"data":{"id":"01a10b54-dd8d-7293-8fa2-875f997a831a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":5,"change_seq":67,"created_at":"2026-10-05T09:11:13.549218Z","updated_at":"2026-10-05T09:14:58.351409Z","deleted_at":"2026-10-05T09:14:58.351409Z","member_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","role":"viewer"},"meta":{}}"""

    /** the body sent */
    const val LEAVE_200_REQUEST = """{"version":4}"""

    /** 409: POST wallets/<id>/leave (POST leave with a stale version (409 current)) */
    const val LEAVE_STALE_409 = """{"type":"http:\/\/saveme-backend.test\/problems\/version-conflict","title":"Version conflict","status":409,"detail":"Someone changed this in the meantime. Review the latest version and try again.","code":"VERSION_CONFLICT","request_id":"01a10b58-4b1c-737c-859c-0a5e5b8cabe2","current":{"id":"01a10b54-dd8d-7293-8fa2-875f997a831a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":4,"change_seq":49,"created_at":"2026-10-05T09:11:13.549218Z","updated_at":"2026-10-05T09:13:20.732905Z","deleted_at":null,"member_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","role":"viewer"}}"""

    /** the body sent */
    const val LEAVE_STALE_409_REQUEST = """{"version":1}"""

    /** 404: POST wallets/<id>/leave (POST leave again (404)) */
    const val LEAVE_REPEAT_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b58-4bf2-7354-8b0c-f4be5ede9a73"}"""

    /** the body sent */
    const val LEAVE_REPEAT_404_REQUEST = """{"version":4}"""

    /** 403: POST wallets/<id>/leave (POST leave by the owner (403 FORBIDDEN)) */
    const val LEAVE_OWNER_403 = """{"type":"http:\/\/saveme-backend.test\/problems\/forbidden","title":"Forbidden","status":403,"detail":"Your role in this wallet does not allow this.","code":"FORBIDDEN","request_id":"01a10b55-ce71-727f-b58d-84e573762298"}"""

    /** the body sent */
    const val LEAVE_OWNER_403_REQUEST = """{"version":1}"""

    /** 200: POST wallets/<id>/leave (POST leave by a viewer (200 tombstone)) */
    const val LEAVE_VIEWER_200 = """{"data":{"id":"01a10b58-014a-709c-8433-cbd6e29d179a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":69,"created_at":"2026-10-05T09:14:39.306467Z","updated_at":"2026-10-05T09:14:59.049881Z","deleted_at":"2026-10-05T09:14:59.049881Z","member_user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","role":"viewer"},"meta":{}}"""

    /** the body sent */
    const val LEAVE_VIEWER_200_REQUEST = """{"version":1}"""

    /** 200: GET wallets/<id>/activity?limit=50 (GET activity limit 50, whole history, newest first) */
    const val ACTIVITY_PAGE_200 = """{"data":[{"id":"01a10b56-3e8c-73e6-9718-440a84bf148f","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:12:43.916400Z","actor_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","actor_display_name":"ED QA","action":"created","subject_type":"transaction","subject_id":"01a10b56-3de6-71bc-b183-21263d6796fd","role":null},{"id":"01a10b55-cbfd-70ee-8e51-4b67b2cf4798","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:12:14.589994Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"removed","subject_type":"member","subject_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","role":null},{"id":"01a10b55-c731-7253-985b-28473777f187","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:12:13.361885Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"role_changed","subject_type":"member","subject_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","role":"editor"},{"id":"01a10b55-c62e-713f-a96c-c4be73cdeb7d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:12:13.102469Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"role_changed","subject_type":"member","subject_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","role":"viewer"},{"id":"01a10b55-89e0-7240-9f63-3869057a8d36","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:11:57.664532Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"invited","subject_type":"invitation","subject_id":"01a10b55-8951-7d1b-a487-8a65bc35cdf0","role":"viewer"},{"id":"01a10b55-8946-7075-8a32-42e0bee0854f","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:11:57.510902Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"invitation_revoked","subject_type":"invitation","subject_id":"01a10b55-8795-7490-9be7-a4ac1c7837e4","role":null},{"id":"01a10b55-883c-70f9-a46f-b3d8f6ec6272","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:11:57.244582Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"invited","subject_type":"invitation","subject_id":"01a10b55-8795-7490-9be7-a4ac1c7837e4","role":"viewer"},{"id":"01a10b55-8725-71b4-8b65-bf31ecede558","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:11:56.965994Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"invited","subject_type":"invitation","subject_id":"01a10b55-8696-7deb-afd7-6c681051a37e","role":"editor"},{"id":"01a10b55-8546-7309-b701-774fb97293c0","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:11:56.486331Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"invited","subject_type":"invitation","subject_id":"01a10b55-84c5-736b-b406-912a66fc081b","role":"viewer"},{"id":"01a10b55-84b9-71e0-bb4e-77825598d6ec","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:11:56.345929Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"invitation_revoked","subject_type":"invitation","subject_id":"01a10b55-3858-7707-82b6-d4aed7de4fec","role":null},{"id":"01a10b55-82d5-71bb-9412-7bdcdeed4a3f","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:11:55.862098Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"invitation_revoked","subject_type":"invitation","subject_id":"01a10b55-37ad-7382-a68f-7827ca7e9ab9","role":null},{"id":"01a10b55-38e5-7115-abc3-8143f3c265bd","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:11:36.933172Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"invited","subject_type":"invitation","subject_id":"01a10b55-3858-7707-82b6-d4aed7de4fec","role":"viewer"},{"id":"01a10b55-383c-72e8-9c51-5ca5ffb2cff6","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:11:36.764310Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"invited","subject_type":"invitation","subject_id":"01a10b55-37ad-7382-a68f-7827ca7e9ab9","role":"viewer"},{"id":"01a10b54-e1f0-7307-b84c-2e757f257e25","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:11:14.672559Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"invited","subject_type":"invitation","subject_id":"01a10b54-e161-78e7-b545-35ab486cb90c","role":"viewer"},{"id":"01a10b54-e080-71b6-9343-46d8360ec453","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:11:14.304945Z","actor_user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","actor_display_name":null,"action":"joined","subject_type":"member","subject_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","role":"viewer"},{"id":"01a10b54-dd98-7348-b204-deb6664364e3","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:11:13.560872Z","actor_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","actor_display_name":"ED QA","action":"joined","subject_type":"member","subject_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","role":"editor"},{"id":"01a10b54-52b1-721c-b6fe-54b78e95307b","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:10:38.001280Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"invited","subject_type":"invitation","subject_id":"01a10b54-522e-782e-8ab3-ff26abf3a14d","role":"viewer"},{"id":"01a10b54-512f-7114-8de4-1f1ec81a2d35","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:10:37.615742Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"invited","subject_type":"invitation","subject_id":"01a10b54-5094-7ada-a1d8-222ac080f39d","role":"editor"}],"meta":{"next_cursor":null,"has_more":false,"limit":50}}"""

    /** 200: GET wallets/<id>/activity?limit=2 (GET activity limit 2 (next_cursor set)) */
    const val ACTIVITY_LIMIT_TWO_200 = """{"data":[{"id":"01a10b56-3e8c-73e6-9718-440a84bf148f","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:12:43.916400Z","actor_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","actor_display_name":"ED QA","action":"created","subject_type":"transaction","subject_id":"01a10b56-3de6-71bc-b183-21263d6796fd","role":null},{"id":"01a10b55-cbfd-70ee-8e51-4b67b2cf4798","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:12:14.589994Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"removed","subject_type":"member","subject_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","role":null}],"meta":{"next_cursor":"eyJrIjoiMmQ0MWM5ZWIwMjQyZTVjYyIsInAiOnsidmFsdWUiOiIyMDI2LTEwLTA1IDA5OjEyOjE0LjU4OTk5NCswMDowMCIsImlkIjoiMDFhMTBiNTUtY2JmZC03MGVlLThlNTEtNGI2N2IyY2Y0Nzk4In19","has_more":true,"limit":2}}"""

    /** 200: GET wallets/<id>/activity?limit=2&cursor=eyJrIjoiMmQ0MWM5ZWIwMjQyZTVjYyIsInAiOnsidmFsdWUiOiIyMDI2LTEwLTA1IDA5OjEyOjE0LjU4OTk5NCswMDowMCIsImlkIjoiMDFhMTBiNTUtY2JmZC03MGVlLThlNTEtNGI2N2IyY2Y0Nzk4In19 (GET activity limit 2 with the cursor of the first page) */
    const val ACTIVITY_PAGE_TWO_200 = """{"data":[{"id":"01a10b55-c731-7253-985b-28473777f187","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:12:13.361885Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"role_changed","subject_type":"member","subject_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","role":"editor"},{"id":"01a10b55-c62e-713f-a96c-c4be73cdeb7d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:12:13.102469Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"role_changed","subject_type":"member","subject_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","role":"viewer"}],"meta":{"next_cursor":"eyJrIjoiMmQ0MWM5ZWIwMjQyZTVjYyIsInAiOnsidmFsdWUiOiIyMDI2LTEwLTA1IDA5OjEyOjEzLjEwMjQ2OSswMDowMCIsImlkIjoiMDFhMTBiNTUtYzYyZS03MTNmLWE5NmMtYzRiZTczY2RlYjdkIn19","has_more":true,"limit":2}}"""

    /** 400: GET wallets/<id>/activity?limit=50&cursor=garbage (GET activity with a bad cursor (400 INVALID_CURSOR)) */
    const val ACTIVITY_INVALID_CURSOR_400 = """{"type":"http:\/\/saveme-backend.test\/problems\/invalid-cursor","title":"Invalid cursor","status":400,"detail":"The page cursor is not valid. Start again from the first page.","code":"INVALID_CURSOR","request_id":"01a10b56-403b-7018-bd86-b94742178815"}"""

    /** 404: GET wallets/<id>/activity?limit=50 (GET activity by a non-member) */
    const val ACTIVITY_NON_MEMBER_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b56-40f3-71e1-8e49-49c198c73028"}"""

    /** 404: GET wallets/<id>/activity?limit=50 (GET activity after leaving) */
    const val ACTIVITY_LEFT_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b58-4d13-7336-a548-8fb174accda6"}"""

    /** 200: GET wallets/<id>/activity?limit=3 (GET activity limit 3 after the plan demotion (role_limited_by_plan with a null actor)) */
    const val ACTIVITY_AFTER_DEMOTION_200 = """{"data":[{"id":"01a10b56-ce64-70a9-9143-02c7172724cc","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:13:20.740926Z","actor_user_id":null,"actor_display_name":null,"action":"role_limited_by_plan","subject_type":"member","subject_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","role":"viewer"},{"id":"01a10b56-c38a-7165-af53-9fe711b416e0","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:13:17.962295Z","actor_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","actor_display_name":"OWN QA","action":"invited","subject_type":"invitation","subject_id":"01a10b56-c2fb-715c-9888-8f6b1db445b4","role":"viewer"},{"id":"01a10b56-3e8c-73e6-9718-440a84bf148f","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","occurred_at":"2026-10-05T09:12:43.916400Z","actor_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","actor_display_name":"ED QA","action":"created","subject_type":"transaction","subject_id":"01a10b56-3de6-71bc-b183-21263d6796fd","role":null}],"meta":{"next_cursor":"eyJrIjoiMmQ0MWM5ZWIwMjQyZTVjYyIsInAiOnsidmFsdWUiOiIyMDI2LTEwLTA1IDA5OjEyOjQzLjkxNjQwMCswMDowMCIsImlkIjoiMDFhMTBiNTYtM2U4Yy03M2U2LTk3MTgtNDQwYTg0YmYxNDhmIn19","has_more":true,"limit":3}}"""

    /** 403: PUT wallets/<id>/transactions/<id> (PUT a transaction as a demoted viewer (403 FORBIDDEN)) */
    const val TRANSACTION_PUT_VIEWER_403 = """{"type":"http:\/\/saveme-backend.test\/problems\/forbidden","title":"Forbidden","status":403,"detail":"Your role in this wallet does not allow this.","code":"FORBIDDEN","request_id":"01a10b57-20d2-73d7-9d13-084424891076"}"""

    /** the body sent */
    const val TRANSACTION_PUT_VIEWER_403_REQUEST = """{"kind":"expense","date":"2026-10-05","amount_minor":100,"currency":"EGP","category_id":"01a10b56-3d58-73ac-ae5c-ebad3d0e9fec","counter_wallet_id":null,"description":null,"memo":null,"counterparty":null,"cleared":true,"version":0}"""

    /** 200: GET sync/wallets/<id>/changes?limit=100 (Owner's wallet feed: wallet_member, invitation rows (pending, accepted, declined, revoked, expired), transaction, wallet) */
    const val FEED_WALLET_OWNER_ALL_STATUSES_200 = """{"data":[{"type":"wallet_member","record":{"id":"01a10b54-4fb5-7321-8d2f-cd1ffed33ca4","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":2,"created_at":"2026-10-05T09:10:37.237136Z","updated_at":"2026-10-05T09:10:37.237136Z","deleted_at":null,"member_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","display_name":"OWN QA","role":"owner"}},{"type":"invitation","record":{"id":"01a10b54-5094-7ada-a1d8-222ac080f39d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":9,"created_at":"2026-10-05T09:10:37.606530Z","updated_at":"2026-10-05T09:11:13.562589Z","deleted_at":"2026-10-05T09:11:13.562589Z","role":"editor","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"accepted","expires_at":"2026-10-12T09:10:37.598412Z"}},{"type":"invitation","record":{"id":"01a10b54-522e-782e-8ab3-ff26abf3a14d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":12,"created_at":"2026-10-05T09:10:37.996434Z","updated_at":"2026-10-05T09:11:14.306884Z","deleted_at":"2026-10-05T09:11:14.306884Z","role":"viewer","channel":"link","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"accepted","expires_at":"2026-10-12T09:10:37.992977Z"}},{"type":"invitation","record":{"id":"01a10b54-e161-78e7-b545-35ab486cb90c","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":16,"created_at":"2026-10-05T09:11:14.668495Z","updated_at":"2026-10-05T09:11:14.787607Z","deleted_at":"2026-10-05T09:11:14.787607Z","role":"viewer","channel":"link","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"declined","expires_at":"2026-10-12T09:11:14.664660Z"}},{"type":"invitation","record":{"id":"01a10b55-37ad-7382-a68f-7827ca7e9ab9","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":22,"created_at":"2026-10-05T09:11:36.760278Z","updated_at":"2026-10-05T09:11:55.856094Z","deleted_at":"2026-10-05T09:11:55.856094Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:36.752644Z"}},{"type":"invitation","record":{"id":"01a10b55-3858-7707-82b6-d4aed7de4fec","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":24,"created_at":"2026-10-05T09:11:36.928769Z","updated_at":"2026-10-05T09:11:56.342201Z","deleted_at":"2026-10-05T09:11:56.342201Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:36.921315Z"}},{"type":"invitation","record":{"id":"01a10b55-84c5-736b-b406-912a66fc081b","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":28,"created_at":"2026-10-05T09:11:56.482356Z","updated_at":"2026-10-05T09:11:56.950290Z","deleted_at":"2026-10-05T09:11:56.950290Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:56.475176Z"}},{"type":"invitation","record":{"id":"01a10b55-8795-7490-9be7-a4ac1c7837e4","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":33,"created_at":"2026-10-05T09:11:57.239363Z","updated_at":"2026-10-05T09:11:57.505893Z","deleted_at":"2026-10-05T09:11:57.505893Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:57.230592Z"}},{"type":"wallet_member","record":{"id":"01a10b54-e078-7098-becb-f1c55198e003","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":41,"created_at":"2026-10-05T09:11:14.296960Z","updated_at":"2026-10-05T09:12:14.582464Z","deleted_at":"2026-10-05T09:12:14.582464Z","member_user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","role":"viewer"}},{"type":"transaction","record":{"id":"01a10b56-3de6-71bc-b183-21263d6796fd","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":43,"created_at":"2026-10-05T09:12:43.885485Z","updated_at":"2026-10-05T09:12:43.885485Z","deleted_at":null,"kind":"expense","date":"2026-10-05","amount_minor":1500,"currency":"EGP","category_id":"01a10b56-3d58-73ac-ae5c-ebad3d0e9fec","counter_wallet_id":null,"description":"Groceries","memo":null,"counterparty":null,"cleared":true,"created_by":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","recurring_rule_id":null,"occurrence_id":null}},{"type":"category_reference","record":{"id":"01a10b56-3e83-709c-9ddd-c4fe6751aaf1","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":44,"created_at":"2026-10-05T09:12:43.907283Z","updated_at":"2026-10-05T09:12:43.907283Z","deleted_at":null,"category_id":"01a10b56-3d58-73ac-ae5c-ebad3d0e9fec","owner_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","kind":"expense","name":"Groceries","icon":null,"color":null}},{"type":"wallet_member","record":{"id":"01a10b54-dd8d-7293-8fa2-875f997a831a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":4,"change_seq":49,"created_at":"2026-10-05T09:11:13.549218Z","updated_at":"2026-10-05T09:13:20.732905Z","deleted_at":null,"member_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","role":"viewer"}},{"type":"invitation","record":{"id":"01a10b55-8951-7d1b-a487-8a65bc35cdf0","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":51,"created_at":"2026-10-05T09:11:57.658007Z","updated_at":"2026-10-05T09:13:42.482662Z","deleted_at":"2026-10-05T09:13:42.482662Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:57.649524Z"}},{"type":"invitation","record":{"id":"01a10b57-6f30-74e4-b619-f9a58d24a97c","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":55,"created_at":"2026-10-05T09:14:02.037514Z","updated_at":"2026-10-05T09:14:02.037514Z","deleted_at":null,"role":"viewer","channel":"email","invitee_email":"pending-e@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:14:02.028237Z"}},{"type":"invitation","record":{"id":"01a10b57-66c1-71dd-8315-98678137fde2","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":57,"created_at":"2026-10-05T09:13:59.898003Z","updated_at":"2026-10-05T09:14:02.708879Z","deleted_at":"2026-10-05T09:14:02.708879Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-05T08:14:02.000000Z"}},{"type":"invitation","record":{"id":"01a10b55-8696-7deb-afd7-6c681051a37e","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":59,"created_at":"2026-10-05T09:11:56.961843Z","updated_at":"2026-10-05T09:14:03.111078Z","deleted_at":"2026-10-05T09:14:03.111078Z","role":"editor","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"expired","expires_at":"2026-10-05T08:14:00.000000Z"}},{"type":"invitation","record":{"id":"01a10b56-c2fb-715c-9888-8f6b1db445b4","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":60,"created_at":"2026-10-05T09:13:17.957928Z","updated_at":"2026-10-05T09:14:03.111078Z","deleted_at":"2026-10-05T09:14:03.111078Z","role":"viewer","channel":"link","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"expired","expires_at":"2026-10-05T08:14:01.000000Z"}},{"type":"wallet","record":{"id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":61,"created_at":"2026-10-05T09:10:37.229327Z","updated_at":"2026-10-05T09:10:37.229327Z","deleted_at":null,"type":"cash","name":"Home","currency":"EGP","opening_balance_minor":10000,"balance_minor":8500,"icon":null,"color":null,"archived_at":null,"currency_locked":true,"my_role":"owner"}}],"meta":{"next_cursor":null,"has_more":false,"limit":100,"sync_cursor":"eyJ2IjoxLCJmIjoidyIsInMiOiIwMWExMGI1NC00ZjFkLTdjMzktOTk1OC0wNWU4MGRiYzZjNTkiLCJyIjoiMDFhMTBiNTMtYWM3NS03MTJjLThhMTEtNjdhMDYzNzQ2Y2QyIiwicCI6NjF9QYdPcb2m_Nt8SlPJY6DT5A","sync_position":61}}"""

    /** 200: GET sync/wallets/<id>/changes?limit=100 (A member's wallet feed after the rejoin (old member tombstone + new live row)) */
    const val FEED_WALLET_AFTER_REJOIN_200 = """{"data":[{"type":"wallet_member","record":{"id":"01a10b54-4fb5-7321-8d2f-cd1ffed33ca4","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":2,"created_at":"2026-10-05T09:10:37.237136Z","updated_at":"2026-10-05T09:10:37.237136Z","deleted_at":null,"member_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","display_name":"OWN QA","role":"owner"}},{"type":"invitation","record":{"id":"01a10b54-5094-7ada-a1d8-222ac080f39d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":9,"created_at":"2026-10-05T09:10:37.606530Z","updated_at":"2026-10-05T09:11:13.562589Z","deleted_at":"2026-10-05T09:11:13.562589Z","role":"editor","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"accepted","expires_at":"2026-10-12T09:10:37.598412Z"}},{"type":"invitation","record":{"id":"01a10b54-522e-782e-8ab3-ff26abf3a14d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":12,"created_at":"2026-10-05T09:10:37.996434Z","updated_at":"2026-10-05T09:11:14.306884Z","deleted_at":"2026-10-05T09:11:14.306884Z","role":"viewer","channel":"link","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"accepted","expires_at":"2026-10-12T09:10:37.992977Z"}},{"type":"invitation","record":{"id":"01a10b54-e161-78e7-b545-35ab486cb90c","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":16,"created_at":"2026-10-05T09:11:14.668495Z","updated_at":"2026-10-05T09:11:14.787607Z","deleted_at":"2026-10-05T09:11:14.787607Z","role":"viewer","channel":"link","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"declined","expires_at":"2026-10-12T09:11:14.664660Z"}},{"type":"invitation","record":{"id":"01a10b55-37ad-7382-a68f-7827ca7e9ab9","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":22,"created_at":"2026-10-05T09:11:36.760278Z","updated_at":"2026-10-05T09:11:55.856094Z","deleted_at":"2026-10-05T09:11:55.856094Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:36.752644Z"}},{"type":"invitation","record":{"id":"01a10b55-3858-7707-82b6-d4aed7de4fec","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":24,"created_at":"2026-10-05T09:11:36.928769Z","updated_at":"2026-10-05T09:11:56.342201Z","deleted_at":"2026-10-05T09:11:56.342201Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:36.921315Z"}},{"type":"invitation","record":{"id":"01a10b55-84c5-736b-b406-912a66fc081b","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":28,"created_at":"2026-10-05T09:11:56.482356Z","updated_at":"2026-10-05T09:11:56.950290Z","deleted_at":"2026-10-05T09:11:56.950290Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:56.475176Z"}},{"type":"invitation","record":{"id":"01a10b55-8795-7490-9be7-a4ac1c7837e4","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":33,"created_at":"2026-10-05T09:11:57.239363Z","updated_at":"2026-10-05T09:11:57.505893Z","deleted_at":"2026-10-05T09:11:57.505893Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:57.230592Z"}},{"type":"wallet_member","record":{"id":"01a10b54-e078-7098-becb-f1c55198e003","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":41,"created_at":"2026-10-05T09:11:14.296960Z","updated_at":"2026-10-05T09:12:14.582464Z","deleted_at":"2026-10-05T09:12:14.582464Z","member_user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","role":"viewer"}},{"type":"transaction","record":{"id":"01a10b56-3de6-71bc-b183-21263d6796fd","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":43,"created_at":"2026-10-05T09:12:43.885485Z","updated_at":"2026-10-05T09:12:43.885485Z","deleted_at":null,"kind":"expense","date":"2026-10-05","amount_minor":1500,"currency":"EGP","category_id":"01a10b56-3d58-73ac-ae5c-ebad3d0e9fec","counter_wallet_id":null,"description":"Groceries","memo":null,"counterparty":null,"cleared":true,"created_by":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","recurring_rule_id":null,"occurrence_id":null}},{"type":"category_reference","record":{"id":"01a10b56-3e83-709c-9ddd-c4fe6751aaf1","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":44,"created_at":"2026-10-05T09:12:43.907283Z","updated_at":"2026-10-05T09:12:43.907283Z","deleted_at":null,"category_id":"01a10b56-3d58-73ac-ae5c-ebad3d0e9fec","owner_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","kind":"expense","name":"Groceries","icon":null,"color":null}},{"type":"wallet_member","record":{"id":"01a10b54-dd8d-7293-8fa2-875f997a831a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":4,"change_seq":49,"created_at":"2026-10-05T09:11:13.549218Z","updated_at":"2026-10-05T09:13:20.732905Z","deleted_at":null,"member_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","role":"viewer"}},{"type":"invitation","record":{"id":"01a10b55-8951-7d1b-a487-8a65bc35cdf0","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":51,"created_at":"2026-10-05T09:11:57.658007Z","updated_at":"2026-10-05T09:13:42.482662Z","deleted_at":"2026-10-05T09:13:42.482662Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:57.649524Z"}},{"type":"invitation","record":{"id":"01a10b57-6f30-74e4-b619-f9a58d24a97c","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":55,"created_at":"2026-10-05T09:14:02.037514Z","updated_at":"2026-10-05T09:14:02.037514Z","deleted_at":null,"role":"viewer","channel":"email","invitee_email":"pending-e@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:14:02.028237Z"}},{"type":"invitation","record":{"id":"01a10b57-66c1-71dd-8315-98678137fde2","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":57,"created_at":"2026-10-05T09:13:59.898003Z","updated_at":"2026-10-05T09:14:02.708879Z","deleted_at":"2026-10-05T09:14:02.708879Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-05T08:14:02.000000Z"}},{"type":"invitation","record":{"id":"01a10b55-8696-7deb-afd7-6c681051a37e","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":59,"created_at":"2026-10-05T09:11:56.961843Z","updated_at":"2026-10-05T09:14:03.111078Z","deleted_at":"2026-10-05T09:14:03.111078Z","role":"editor","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"expired","expires_at":"2026-10-05T08:14:00.000000Z"}},{"type":"invitation","record":{"id":"01a10b56-c2fb-715c-9888-8f6b1db445b4","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":60,"created_at":"2026-10-05T09:13:17.957928Z","updated_at":"2026-10-05T09:14:03.111078Z","deleted_at":"2026-10-05T09:14:03.111078Z","role":"viewer","channel":"link","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"expired","expires_at":"2026-10-05T08:14:01.000000Z"}},{"type":"wallet_member","record":{"id":"01a10b58-014a-709c-8433-cbd6e29d179a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":64,"created_at":"2026-10-05T09:14:39.306467Z","updated_at":"2026-10-05T09:14:39.306467Z","deleted_at":null,"member_user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","role":"viewer"}},{"type":"invitation","record":{"id":"01a10b58-0022-7d36-abae-15adf60ac79f","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":65,"created_at":"2026-10-05T09:14:39.164863Z","updated_at":"2026-10-05T09:14:39.315800Z","deleted_at":"2026-10-05T09:14:39.315800Z","role":"viewer","channel":"link","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"accepted","expires_at":"2026-10-12T09:14:39.159994Z"}},{"type":"wallet","record":{"id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":66,"created_at":"2026-10-05T09:10:37.229327Z","updated_at":"2026-10-05T09:10:37.229327Z","deleted_at":null,"type":"cash","name":"Home","currency":"EGP","opening_balance_minor":10000,"balance_minor":8500,"icon":null,"color":null,"archived_at":null,"currency_locked":true,"my_role":"viewer"}}],"meta":{"next_cursor":null,"has_more":false,"limit":100,"sync_cursor":"eyJ2IjoxLCJmIjoidyIsInMiOiIwMWExMGI1NC00ZjFkLTdjMzktOTk1OC0wNWU4MGRiYzZjNTkiLCJyIjoiMDFhMTBiNTMtYzczYy03MDY4LWI4MmYtZDI2NzZlNzkzYmI5IiwicCI6NjZ9RUOnQ7cC4AfIN9Awedf7ZA","sync_position":66}}"""

    /** 200: GET sync/wallets/<id>/changes?limit=100 (The demoted editor's wallet feed (own member row now viewer, v4)) */
    const val FEED_WALLET_AFTER_DEMOTION_200 = """{"data":[{"type":"wallet_member","record":{"id":"01a10b54-4fb5-7321-8d2f-cd1ffed33ca4","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":2,"created_at":"2026-10-05T09:10:37.237136Z","updated_at":"2026-10-05T09:10:37.237136Z","deleted_at":null,"member_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","display_name":"OWN QA","role":"owner"}},{"type":"invitation","record":{"id":"01a10b54-5094-7ada-a1d8-222ac080f39d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":9,"created_at":"2026-10-05T09:10:37.606530Z","updated_at":"2026-10-05T09:11:13.562589Z","deleted_at":"2026-10-05T09:11:13.562589Z","role":"editor","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"accepted","expires_at":"2026-10-12T09:10:37.598412Z"}},{"type":"invitation","record":{"id":"01a10b54-522e-782e-8ab3-ff26abf3a14d","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":12,"created_at":"2026-10-05T09:10:37.996434Z","updated_at":"2026-10-05T09:11:14.306884Z","deleted_at":"2026-10-05T09:11:14.306884Z","role":"viewer","channel":"link","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"accepted","expires_at":"2026-10-12T09:10:37.992977Z"}},{"type":"invitation","record":{"id":"01a10b54-e161-78e7-b545-35ab486cb90c","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":16,"created_at":"2026-10-05T09:11:14.668495Z","updated_at":"2026-10-05T09:11:14.787607Z","deleted_at":"2026-10-05T09:11:14.787607Z","role":"viewer","channel":"link","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"declined","expires_at":"2026-10-12T09:11:14.664660Z"}},{"type":"invitation","record":{"id":"01a10b55-37ad-7382-a68f-7827ca7e9ab9","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":22,"created_at":"2026-10-05T09:11:36.760278Z","updated_at":"2026-10-05T09:11:55.856094Z","deleted_at":"2026-10-05T09:11:55.856094Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:36.752644Z"}},{"type":"invitation","record":{"id":"01a10b55-3858-7707-82b6-d4aed7de4fec","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":24,"created_at":"2026-10-05T09:11:36.928769Z","updated_at":"2026-10-05T09:11:56.342201Z","deleted_at":"2026-10-05T09:11:56.342201Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:36.921315Z"}},{"type":"invitation","record":{"id":"01a10b55-84c5-736b-b406-912a66fc081b","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":28,"created_at":"2026-10-05T09:11:56.482356Z","updated_at":"2026-10-05T09:11:56.950290Z","deleted_at":"2026-10-05T09:11:56.950290Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:56.475176Z"}},{"type":"invitation","record":{"id":"01a10b55-8696-7deb-afd7-6c681051a37e","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":29,"created_at":"2026-10-05T09:11:56.961843Z","updated_at":"2026-10-05T09:11:56.961843Z","deleted_at":null,"role":"editor","channel":"email","invitee_email":"pending-a@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:11:56.950290Z"}},{"type":"invitation","record":{"id":"01a10b55-8795-7490-9be7-a4ac1c7837e4","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":33,"created_at":"2026-10-05T09:11:57.239363Z","updated_at":"2026-10-05T09:11:57.505893Z","deleted_at":"2026-10-05T09:11:57.505893Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:11:57.230592Z"}},{"type":"invitation","record":{"id":"01a10b55-8951-7d1b-a487-8a65bc35cdf0","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":35,"created_at":"2026-10-05T09:11:57.658007Z","updated_at":"2026-10-05T09:11:57.658007Z","deleted_at":null,"role":"viewer","channel":"email","invitee_email":"pending-c@example.com","invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:11:57.649524Z"}},{"type":"wallet_member","record":{"id":"01a10b54-e078-7098-becb-f1c55198e003","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":41,"created_at":"2026-10-05T09:11:14.296960Z","updated_at":"2026-10-05T09:12:14.582464Z","deleted_at":"2026-10-05T09:12:14.582464Z","member_user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","role":"viewer"}},{"type":"transaction","record":{"id":"01a10b56-3de6-71bc-b183-21263d6796fd","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":43,"created_at":"2026-10-05T09:12:43.885485Z","updated_at":"2026-10-05T09:12:43.885485Z","deleted_at":null,"kind":"expense","date":"2026-10-05","amount_minor":1500,"currency":"EGP","category_id":"01a10b56-3d58-73ac-ae5c-ebad3d0e9fec","counter_wallet_id":null,"description":"Groceries","memo":null,"counterparty":null,"cleared":true,"created_by":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","recurring_rule_id":null,"occurrence_id":null}},{"type":"category_reference","record":{"id":"01a10b56-3e83-709c-9ddd-c4fe6751aaf1","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":44,"created_at":"2026-10-05T09:12:43.907283Z","updated_at":"2026-10-05T09:12:43.907283Z","deleted_at":null,"category_id":"01a10b56-3d58-73ac-ae5c-ebad3d0e9fec","owner_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","kind":"expense","name":"Groceries","icon":null,"color":null}},{"type":"invitation","record":{"id":"01a10b56-c2fb-715c-9888-8f6b1db445b4","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":47,"created_at":"2026-10-05T09:13:17.957928Z","updated_at":"2026-10-05T09:13:17.957928Z","deleted_at":null,"role":"viewer","channel":"link","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"pending","expires_at":"2026-10-12T09:13:17.953067Z"}},{"type":"wallet_member","record":{"id":"01a10b54-dd8d-7293-8fa2-875f997a831a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":4,"change_seq":49,"created_at":"2026-10-05T09:11:13.549218Z","updated_at":"2026-10-05T09:13:20.732905Z","deleted_at":null,"member_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","role":"viewer"}},{"type":"wallet","record":{"id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":50,"created_at":"2026-10-05T09:10:37.229327Z","updated_at":"2026-10-05T09:10:37.229327Z","deleted_at":null,"type":"cash","name":"Home","currency":"EGP","opening_balance_minor":10000,"balance_minor":8500,"icon":null,"color":null,"archived_at":null,"currency_locked":true,"my_role":"viewer"}}],"meta":{"next_cursor":null,"has_more":false,"limit":100,"sync_cursor":"eyJ2IjoxLCJmIjoidyIsInMiOiIwMWExMGI1NC00ZjFkLTdjMzktOTk1OC0wNWU4MGRiYzZjNTkiLCJyIjoiMDFhMTBiNTMtYzRhMi03MmM1LTliOWUtYmMwMzgyYmQzZjA2IiwicCI6NTB9jhETGITpU5KjMn8ZAttb-Q","sync_position":50}}"""

    /** 200: GET sync/user/changes?limit=100 (A member's user feed: own live wallet_member row (editor v3)) */
    const val FEED_USER_OWN_MEMBERSHIP_200 = """{"data":[{"type":"profile","record":{"id":"01a10b53-c4ac-718b-93a7-44202a49b6d5","version":1,"change_seq":1,"created_at":"2026-10-05T09:10:01.644332Z","updated_at":"2026-10-05T09:10:01.644332Z","deleted_at":null,"user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","locale":"en","timezone":"Africa\/Cairo","default_currency":"EGP","default_currency_locked":false,"budget_period_start_day":1,"budget_period_start_day_from":null}},{"type":"device_session","record":{"id":"01a10b53-f2d8-7172-bafd-0ba7748c5e06","version":2,"change_seq":3,"created_at":"2026-10-05T09:10:13.764427Z","updated_at":"2026-10-05T09:10:24.540124Z","deleted_at":null,"user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","device_name":"QA ed","platform":"android","app_version":"1.0.0","state":"revoked","end_reason":"replaced","signed_in_at":"2026-10-05T09:10:13.762811Z","last_used_at":null,"ended_at":"2026-10-05T09:10:24.539277Z","current":false}},{"type":"device_session","record":{"id":"01a10b54-1cf5-743e-9aec-896bc362360c","version":1,"change_seq":4,"created_at":"2026-10-05T09:10:24.546613Z","updated_at":"2026-10-05T09:12:15.332239Z","deleted_at":null,"user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","device_name":"QA ed","platform":"android","app_version":"1.0.0","state":"active","end_reason":null,"signed_in_at":"2026-10-05T09:10:24.545086Z","last_used_at":"2026-10-05T09:12:15.331742Z","ended_at":null,"current":true}},{"type":"wallet_member","record":{"id":"01a10b54-dd8d-7293-8fa2-875f997a831a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":3,"change_seq":39,"created_at":"2026-10-05T09:11:13.549218Z","updated_at":"2026-10-05T09:12:13.354486Z","deleted_at":null,"member_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","role":"editor"}}],"meta":{"next_cursor":null,"has_more":false,"limit":100,"sync_cursor":"eyJ2IjoxLCJmIjoidSIsInMiOiIwMWExMGI1My1jNGEyLTcyYzUtOWI5ZS1iYzAzODJiZDNmMDYiLCJyIjoiMDFhMTBiNTMtYzRhMi03MmM1LTliOWUtYmMwMzgyYmQzZjA2IiwicCI6N30qS4e_CoiEh3TXknwPKM6j","sync_position":7}}"""

    /** 200: GET sync/user/changes?limit=100 (The demoted editor's user feed: own wallet_member row viewer v4) */
    const val FEED_USER_AFTER_DEMOTION_200 = """{"data":[{"type":"profile","record":{"id":"01a10b53-c4ac-718b-93a7-44202a49b6d5","version":1,"change_seq":1,"created_at":"2026-10-05T09:10:01.644332Z","updated_at":"2026-10-05T09:10:01.644332Z","deleted_at":null,"user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","locale":"en","timezone":"Africa\/Cairo","default_currency":"EGP","default_currency_locked":true,"budget_period_start_day":1,"budget_period_start_day_from":null}},{"type":"device_session","record":{"id":"01a10b53-f2d8-7172-bafd-0ba7748c5e06","version":2,"change_seq":3,"created_at":"2026-10-05T09:10:13.764427Z","updated_at":"2026-10-05T09:10:24.540124Z","deleted_at":null,"user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","device_name":"QA ed","platform":"android","app_version":"1.0.0","state":"revoked","end_reason":"replaced","signed_in_at":"2026-10-05T09:10:13.762811Z","last_used_at":null,"ended_at":"2026-10-05T09:10:24.539277Z","current":false}},{"type":"device_session","record":{"id":"01a10b54-1cf5-743e-9aec-896bc362360c","version":1,"change_seq":4,"created_at":"2026-10-05T09:10:24.546613Z","updated_at":"2026-10-05T09:13:31.963845Z","deleted_at":null,"user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","device_name":"QA ed","platform":"android","app_version":"1.0.0","state":"active","end_reason":null,"signed_in_at":"2026-10-05T09:10:24.545086Z","last_used_at":"2026-10-05T09:13:31.963511Z","ended_at":null,"current":true}},{"type":"category","record":{"id":"01a10b56-3d58-73ac-ae5c-ebad3d0e9fec","user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","version":1,"change_seq":8,"created_at":"2026-10-05T09:12:43.728902Z","updated_at":"2026-10-05T09:12:43.728902Z","deleted_at":null,"kind":"expense","name":"Groceries","icon":null,"color":null,"archived_at":null,"in_use":true}},{"type":"wallet_member","record":{"id":"01a10b54-dd8d-7293-8fa2-875f997a831a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":4,"change_seq":49,"created_at":"2026-10-05T09:11:13.549218Z","updated_at":"2026-10-05T09:13:20.732905Z","deleted_at":null,"member_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","role":"viewer"}}],"meta":{"next_cursor":null,"has_more":false,"limit":100,"sync_cursor":"eyJ2IjoxLCJmIjoidSIsInMiOiIwMWExMGI1My1jNGEyLTcyYzUtOWI5ZS1iYzAzODJiZDNmMDYiLCJyIjoiMDFhMTBiNTMtYzRhMi03MmM1LTliOWUtYmMwMzgyYmQzZjA2IiwicCI6OX3mp0IHtWiAkq9D87reSKde","sync_position":9}}"""

    /** 200: GET sync/user/changes?limit=100 (A removed member's user feed: own wallet_member tombstone) */
    const val FEED_USER_REMOVED_200 = """{"data":[{"type":"profile","record":{"id":"01a10b53-c744-71e4-880b-e3954644be0e","version":1,"change_seq":1,"created_at":"2026-10-05T09:10:02.308822Z","updated_at":"2026-10-05T09:10:02.308822Z","deleted_at":null,"user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","locale":"en","timezone":"Africa\/Cairo","default_currency":"EGP","default_currency_locked":false,"budget_period_start_day":1,"budget_period_start_day_from":null}},{"type":"device_session","record":{"id":"01a10b53-f7dc-72e9-8b0f-60e77a0bc21f","version":2,"change_seq":3,"created_at":"2026-10-05T09:10:15.052936Z","updated_at":"2026-10-05T09:10:25.251568Z","deleted_at":null,"user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","device_name":"QA unv","platform":"android","app_version":"1.0.0","state":"revoked","end_reason":"replaced","signed_in_at":"2026-10-05T09:10:15.051261Z","last_used_at":null,"ended_at":"2026-10-05T09:10:25.250596Z","current":false}},{"type":"device_session","record":{"id":"01a10b54-1f85-755c-8c5a-e0a37c99b45e","version":1,"change_seq":4,"created_at":"2026-10-05T09:10:25.258520Z","updated_at":"2026-10-05T09:12:14.874536Z","deleted_at":null,"user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","device_name":"QA unv","platform":"android","app_version":"1.0.0","state":"active","end_reason":null,"signed_in_at":"2026-10-05T09:10:25.257014Z","last_used_at":"2026-10-05T09:12:14.874082Z","ended_at":null,"current":true}},{"type":"wallet_member","record":{"id":"01a10b54-e078-7098-becb-f1c55198e003","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":41,"created_at":"2026-10-05T09:11:14.296960Z","updated_at":"2026-10-05T09:12:14.582464Z","deleted_at":"2026-10-05T09:12:14.582464Z","member_user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","role":"viewer"}}],"meta":{"next_cursor":null,"has_more":false,"limit":100,"sync_cursor":"eyJ2IjoxLCJmIjoidSIsInMiOiIwMWExMGI1My1jNzNjLTcwNjgtYjgyZi1kMjY3NmU3OTNiYjkiLCJyIjoiMDFhMTBiNTMtYzczYy03MDY4LWI4MmYtZDI2NzZlNzkzYmI5IiwicCI6Nn3389f63HedTRJ9_oP9d8pR","sync_position":6}}"""

    /** 200: GET sync/user/changes?limit=100 (User feed after the rejoin: old tombstone then a new live own row) */
    const val FEED_USER_AFTER_REJOIN_200 = """{"data":[{"type":"profile","record":{"id":"01a10b53-c744-71e4-880b-e3954644be0e","version":1,"change_seq":1,"created_at":"2026-10-05T09:10:02.308822Z","updated_at":"2026-10-05T09:10:02.308822Z","deleted_at":null,"user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","locale":"en","timezone":"Africa\/Cairo","default_currency":"EGP","default_currency_locked":false,"budget_period_start_day":1,"budget_period_start_day_from":null}},{"type":"device_session","record":{"id":"01a10b53-f7dc-72e9-8b0f-60e77a0bc21f","version":2,"change_seq":3,"created_at":"2026-10-05T09:10:15.052936Z","updated_at":"2026-10-05T09:10:25.251568Z","deleted_at":null,"user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","device_name":"QA unv","platform":"android","app_version":"1.0.0","state":"revoked","end_reason":"replaced","signed_in_at":"2026-10-05T09:10:15.051261Z","last_used_at":null,"ended_at":"2026-10-05T09:10:25.250596Z","current":false}},{"type":"device_session","record":{"id":"01a10b54-1f85-755c-8c5a-e0a37c99b45e","version":1,"change_seq":4,"created_at":"2026-10-05T09:10:25.258520Z","updated_at":"2026-10-05T09:14:39.408303Z","deleted_at":null,"user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","device_name":"QA unv","platform":"android","app_version":"1.0.0","state":"active","end_reason":null,"signed_in_at":"2026-10-05T09:10:25.257014Z","last_used_at":"2026-10-05T09:14:39.407919Z","ended_at":null,"current":true}},{"type":"wallet_member","record":{"id":"01a10b54-e078-7098-becb-f1c55198e003","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":2,"change_seq":41,"created_at":"2026-10-05T09:11:14.296960Z","updated_at":"2026-10-05T09:12:14.582464Z","deleted_at":"2026-10-05T09:12:14.582464Z","member_user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","role":"viewer"}},{"type":"wallet_member","record":{"id":"01a10b58-014a-709c-8433-cbd6e29d179a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":1,"change_seq":64,"created_at":"2026-10-05T09:14:39.306467Z","updated_at":"2026-10-05T09:14:39.306467Z","deleted_at":null,"member_user_id":"01a10b53-c73c-7068-b82f-d2676e793bb9","display_name":"UNV QA","role":"viewer"}}],"meta":{"next_cursor":null,"has_more":false,"limit":100,"sync_cursor":"eyJ2IjoxLCJmIjoidSIsInMiOiIwMWExMGI1My1jNzNjLTcwNjgtYjgyZi1kMjY3NmU3OTNiYjkiLCJyIjoiMDFhMTBiNTMtYzczYy03MDY4LWI4MmYtZDI2NzZlNzkzYmI5IiwicCI6N33cysFaWutSiMKnaNFf4kv0","sync_position":7}}"""

    /** 200: GET sync/user/changes?limit=100 (User feed after leaving: own wallet_member tombstone) */
    const val FEED_USER_LEFT_200 = """{"data":[{"type":"profile","record":{"id":"01a10b53-c4ac-718b-93a7-44202a49b6d5","version":1,"change_seq":1,"created_at":"2026-10-05T09:10:01.644332Z","updated_at":"2026-10-05T09:10:01.644332Z","deleted_at":null,"user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","locale":"en","timezone":"Africa\/Cairo","default_currency":"EGP","default_currency_locked":true,"budget_period_start_day":1,"budget_period_start_day_from":null}},{"type":"device_session","record":{"id":"01a10b53-f2d8-7172-bafd-0ba7748c5e06","version":2,"change_seq":3,"created_at":"2026-10-05T09:10:13.764427Z","updated_at":"2026-10-05T09:10:24.540124Z","deleted_at":null,"user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","device_name":"QA ed","platform":"android","app_version":"1.0.0","state":"revoked","end_reason":"replaced","signed_in_at":"2026-10-05T09:10:13.762811Z","last_used_at":null,"ended_at":"2026-10-05T09:10:24.539277Z","current":false}},{"type":"device_session","record":{"id":"01a10b54-1cf5-743e-9aec-896bc362360c","version":1,"change_seq":4,"created_at":"2026-10-05T09:10:24.546613Z","updated_at":"2026-10-05T09:14:58.531243Z","deleted_at":null,"user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","device_name":"QA ed","platform":"android","app_version":"1.0.0","state":"active","end_reason":null,"signed_in_at":"2026-10-05T09:10:24.545086Z","last_used_at":"2026-10-05T09:14:58.530786Z","ended_at":null,"current":true}},{"type":"category","record":{"id":"01a10b56-3d58-73ac-ae5c-ebad3d0e9fec","user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","version":1,"change_seq":8,"created_at":"2026-10-05T09:12:43.728902Z","updated_at":"2026-10-05T09:12:43.728902Z","deleted_at":null,"kind":"expense","name":"Groceries","icon":null,"color":null,"archived_at":null,"in_use":true}},{"type":"wallet_member","record":{"id":"01a10b54-dd8d-7293-8fa2-875f997a831a","wallet_id":"01a10b54-4f1d-7c39-9958-05e80dbc6c59","version":5,"change_seq":67,"created_at":"2026-10-05T09:11:13.549218Z","updated_at":"2026-10-05T09:14:58.351409Z","deleted_at":"2026-10-05T09:14:58.351409Z","member_user_id":"01a10b53-c4a2-72c5-9b9e-bc0382bd3f06","display_name":"ED QA","role":"viewer"}}],"meta":{"next_cursor":null,"has_more":false,"limit":100,"sync_cursor":"eyJ2IjoxLCJmIjoidSIsInMiOiIwMWExMGI1My1jNGEyLTcyYzUtOWI5ZS1iYzAzODJiZDNmMDYiLCJyIjoiMDFhMTBiNTMtYzRhMi03MmM1LTliOWUtYmMwMzgyYmQzZjA2IiwicCI6MTB9fo7807js7sEzBEOcZ_DEiw","sync_position":10}}"""

    /** 404: GET sync/wallets/<id>/changes?limit=100 (Wallet feed of a removed member (404)) */
    const val FEED_WALLET_REMOVED_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b55-cd6a-7143-acee-b2fa457216a9"}"""

    /** 404: GET sync/wallets/<id>/changes?limit=100 (Wallet feed after leaving (404)) */
    const val FEED_WALLET_LEFT_404 = """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10b58-4cbd-710d-a53f-3a0c1e96f487"}"""

    /** 200: GET sync/wallets/<id>/changes?limit=100 (Owner's wallet feed after the wallet was deleted: member, invitation and wallet tombstones) */
    const val FEED_WALLET_DELETED_200 = """{"data":[{"type":"wallet_member","record":{"id":"01a10b58-084a-714d-a426-23741eb97053","wallet_id":"01a10b58-07c7-7759-aa35-ef64c004d441","version":2,"change_seq":6,"created_at":"2026-10-05T09:14:41.098248Z","updated_at":"2026-10-05T09:14:41.365852Z","deleted_at":"2026-10-05T09:14:41.365852Z","member_user_id":"01a10b53-ac75-712c-8a11-67a063746cd2","display_name":"OWN QA","role":"owner"}},{"type":"invitation","record":{"id":"01a10b58-0857-7c14-8b9f-cee74fe34dd5","wallet_id":"01a10b58-07c7-7759-aa35-ef64c004d441","version":2,"change_seq":7,"created_at":"2026-10-05T09:14:41.243540Z","updated_at":"2026-10-05T09:14:41.365852Z","deleted_at":"2026-10-05T09:14:41.365852Z","role":"viewer","channel":"link","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:14:41.238905Z"}},{"type":"wallet","record":{"id":"01a10b58-07c7-7759-aa35-ef64c004d441","wallet_id":"01a10b58-07c7-7759-aa35-ef64c004d441","version":2,"change_seq":8,"created_at":"2026-10-05T09:14:41.093367Z","updated_at":"2026-10-05T09:14:41.393169Z","deleted_at":"2026-10-05T09:14:41.365852Z","type":"cash","name":"Doomed","currency":"EGP","opening_balance_minor":0,"balance_minor":0,"icon":null,"color":null,"archived_at":null,"currency_locked":false,"my_role":"owner"}}],"meta":{"next_cursor":null,"has_more":false,"limit":100,"sync_cursor":"eyJ2IjoxLCJmIjoidyIsInMiOiIwMWExMGI1OC0wN2M3LTc3NTktYWEzNS1lZjY0YzAwNGQ0NDEiLCJyIjoiMDFhMTBiNTMtYWM3NS03MTJjLThhMTEtNjdhMDYzNzQ2Y2QyIiwicCI6OH1gr1NZxm4Rdqmb_wC7fChA","sync_position":8}}"""

    /** 409: PUT wallets/<id>/invitations/<id> (PUT a lost-answer retry (same body, version 0) after the invitation was withdrawn (409 current = the revoked tombstone)) */
    const val PUT_REPLAY_AFTER_CLOSE_409 = """{"type":"http:\/\/saveme-backend.test\/problems\/version-conflict","title":"Version conflict","status":409,"detail":"Someone changed this in the meantime. Review the latest version and try again.","code":"VERSION_CONFLICT","request_id":"01a10b5a-60ad-718c-a414-4419f7e4351e","current":{"id":"01a10b5a-5f61-7401-81d1-00686c68e955","wallet_id":"01a10b5a-5ed9-7947-a2a5-4ce2cc835619","version":2,"change_seq":6,"created_at":"2026-10-05T09:17:14.601831Z","updated_at":"2026-10-05T09:17:14.723658Z","deleted_at":"2026-10-05T09:17:14.723658Z","role":"viewer","channel":"email","invitee_email":null,"invited_by":"01a10b53-ac75-712c-8a11-67a063746cd2","status":"revoked","expires_at":"2026-10-12T09:17:14.594002Z"}}"""

    /** the body sent */
    const val PUT_REPLAY_AFTER_CLOSE_409_REQUEST = """{"role":"viewer","invitee_email":"closed@example.com","version":0}"""
}
