package com.fares.saveme.feature.account

import com.fares.saveme.feature.billing.Captured

/**
 * Real answers of the export and deletion endpoints captured on 2026-10-06 from the local backend
 * (saveme-backend@776e5b2, spec 022, `exports.yaml` / `identity.yaml` v1.13) with throwaway accounts (specs/016-export-deletion/qa-report.md).
 * Every signed download link has its signature replaced by a fake of the same shape (64 hex); no bearer credential, password or
 * address of a real person is kept. The cursors are the server's opaque, signature-free values.
 */
object CapturedDeletionPlanResponses {

    /** Live `d_plan_active`. */
    val planActiveOnlyMemberAndEditor = Captured(200, listOf(), """{"data":{"state":"active","deletion_requested_at":null,"deletion_scheduled_for":null,"successors":[{"wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","successor_user_id":"01a10e92-3627-702f-a813-a5d792dd4365","reason":"only_member","choice_expected":false},{"wallet_id":"01a10e97-a75f-7d07-a16c-84044a9b88ef","successor_user_id":"01a10e97-5def-7013-8854-a66555c62d0d","reason":"oldest_editor","choice_expected":true}]},"meta":{}}""")

    /** Live `d_plan_active_three_wallets`. */
    val planActiveThreeWallets = Captured(200, listOf(), """{"data":{"state":"active","deletion_requested_at":null,"deletion_scheduled_for":null,"successors":[{"wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","successor_user_id":"01a10e92-3627-702f-a813-a5d792dd4365","reason":"only_member","choice_expected":false},{"wallet_id":"01a10e97-a75f-7d07-a16c-84044a9b88ef","successor_user_id":"01a10e97-5def-7013-8854-a66555c62d0d","reason":"oldest_editor","choice_expected":true},{"wallet_id":"01a10e97-e388-78d2-a382-fc534affb96e","successor_user_id":"01a10e97-5f2b-705f-b53f-ae547fadac56","reason":"oldest_viewer","choice_expected":true}]},"meta":{}}""")

    /** Live `d_plan_401`. */
    val planUnauthenticated = Captured(401, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/unauthenticated","title":"Unauthenticated","status":401,"detail":"Please sign in again.","code":"UNAUTHENTICATED","request_id":"01a10e97-b8a8-7344-8886-a38de20fa013"}""")

    /** Live `d_plan_pending_no_choices`. */
    val planPendingNoChoices = Captured(200, listOf(), """{"data":{"state":"pending_deletion","deletion_requested_at":"2026-10-06T00:23:26.934042Z","deletion_scheduled_for":"2026-11-05T00:23:26.934042Z","successors":[{"wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","successor_user_id":"01a10e92-3627-702f-a813-a5d792dd4365","reason":"only_member","choice_expected":false},{"wallet_id":"01a10e97-a75f-7d07-a16c-84044a9b88ef","successor_user_id":"01a10e97-5def-7013-8854-a66555c62d0d","reason":"oldest_editor","choice_expected":true},{"wallet_id":"01a10e97-e388-78d2-a382-fc534affb96e","successor_user_id":"01a10e97-5f2b-705f-b53f-ae547fadac56","reason":"oldest_viewer","choice_expected":true}]},"meta":{}}""")

    /** Live `d_plan_pending_chosen`. */
    val planPendingChosen = Captured(200, listOf(), """{"data":{"state":"pending_deletion","deletion_requested_at":"2026-10-06T00:23:26.934042Z","deletion_scheduled_for":"2026-11-05T00:23:26.934042Z","successors":[{"wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","successor_user_id":"01a10e92-3627-702f-a813-a5d792dd4365","reason":"only_member","choice_expected":false},{"wallet_id":"01a10e97-a75f-7d07-a16c-84044a9b88ef","successor_user_id":"01a10e97-5f2b-705f-b53f-ae547fadac56","reason":"chosen","choice_expected":true},{"wallet_id":"01a10e97-e388-78d2-a382-fc534affb96e","successor_user_id":"01a10e92-3627-702f-a813-a5d792dd4365","reason":"chosen","choice_expected":true}]},"meta":{}}""")

    /** Live `d_plan_pending_replaced`. */
    val planPendingReplaced = Captured(200, listOf(), """{"data":{"state":"pending_deletion","deletion_requested_at":"2026-10-06T00:23:26.934042Z","deletion_scheduled_for":"2026-11-05T00:23:26.934042Z","successors":[{"wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","successor_user_id":"01a10e92-3627-702f-a813-a5d792dd4365","reason":"only_member","choice_expected":false},{"wallet_id":"01a10e97-a75f-7d07-a16c-84044a9b88ef","successor_user_id":"01a10e97-5def-7013-8854-a66555c62d0d","reason":"chosen","choice_expected":true},{"wallet_id":"01a10e97-e388-78d2-a382-fc534affb96e","successor_user_id":"01a10e97-5f2b-705f-b53f-ae547fadac56","reason":"oldest_viewer","choice_expected":true}]},"meta":{}}""")

    /** Live `d_plan_after_cancel`. */
    val planAfterCancel = Captured(200, listOf(), """{"data":{"state":"active","deletion_requested_at":null,"deletion_scheduled_for":null,"successors":[{"wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","successor_user_id":"01a10e92-3627-702f-a813-a5d792dd4365","reason":"only_member","choice_expected":false},{"wallet_id":"01a10e97-a75f-7d07-a16c-84044a9b88ef","successor_user_id":"01a10e97-5def-7013-8854-a66555c62d0d","reason":"oldest_editor","choice_expected":true},{"wallet_id":"01a10e97-e388-78d2-a382-fc534affb96e","successor_user_id":"01a10e97-5f2b-705f-b53f-ae547fadac56","reason":"oldest_viewer","choice_expected":true}]},"meta":{}}""")

    /** Live `d_plan_pending_no_wallets`. */
    val planPendingNoWallets = Captured(200, listOf(), """{"data":{"state":"pending_deletion","deletion_requested_at":"2026-10-06T00:24:42.511335Z","deletion_scheduled_for":"2026-11-05T00:24:42.511335Z","successors":[]},"meta":{}}""")

    /** Live `d_plan_before_purge`. */
    val planBeforePurge = Captured(200, listOf(), """{"data":{"state":"pending_deletion","deletion_requested_at":"2026-10-06T00:23:44.434308Z","deletion_scheduled_for":"2026-11-05T00:23:44.434308Z","successors":[{"wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","successor_user_id":"01a10e92-3627-702f-a813-a5d792dd4365","reason":"only_member","choice_expected":false},{"wallet_id":"01a10e97-a75f-7d07-a16c-84044a9b88ef","successor_user_id":"01a10e97-5f2b-705f-b53f-ae547fadac56","reason":"chosen","choice_expected":true},{"wallet_id":"01a10e97-e388-78d2-a382-fc534affb96e","successor_user_id":"01a10e97-5f2b-705f-b53f-ae547fadac56","reason":"oldest_viewer","choice_expected":true}]},"meta":{}}""")

    /** Live `d_post_200_choice_on_only_member_wallet`. */
    val requestFirstScheduled = Captured(200, listOf(), """{"data":{"state":"pending_deletion","deletion_scheduled_for":"2026-11-05T00:23:26.934042Z","successors":[{"wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","successor_user_id":"01a10e92-3627-702f-a813-a5d792dd4365","reason":"only_member","choice_expected":false},{"wallet_id":"01a10e97-a75f-7d07-a16c-84044a9b88ef","successor_user_id":"01a10e97-5def-7013-8854-a66555c62d0d","reason":"oldest_editor","choice_expected":true},{"wallet_id":"01a10e97-e388-78d2-a382-fc534affb96e","successor_user_id":"01a10e97-5f2b-705f-b53f-ae547fadac56","reason":"oldest_viewer","choice_expected":true}]},"meta":{}}""")

    /** Live `d_post_200_replace_password_only`. */
    val requestRepeatPasswordOnly = Captured(200, listOf(), """{"data":{"state":"pending_deletion","deletion_scheduled_for":"2026-11-05T00:23:26.934042Z","successors":[{"wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","successor_user_id":"01a10e92-3627-702f-a813-a5d792dd4365","reason":"only_member","choice_expected":false},{"wallet_id":"01a10e97-a75f-7d07-a16c-84044a9b88ef","successor_user_id":"01a10e97-5def-7013-8854-a66555c62d0d","reason":"oldest_editor","choice_expected":true},{"wallet_id":"01a10e97-e388-78d2-a382-fc534affb96e","successor_user_id":"01a10e97-5f2b-705f-b53f-ae547fadac56","reason":"oldest_viewer","choice_expected":true}]},"meta":{}}""")

    /** Live `d_post_200_with_choices`. */
    val requestWithChoices = Captured(200, listOf(), """{"data":{"state":"pending_deletion","deletion_scheduled_for":"2026-11-05T00:23:26.934042Z","successors":[{"wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","successor_user_id":"01a10e92-3627-702f-a813-a5d792dd4365","reason":"only_member","choice_expected":false},{"wallet_id":"01a10e97-a75f-7d07-a16c-84044a9b88ef","successor_user_id":"01a10e97-5f2b-705f-b53f-ae547fadac56","reason":"chosen","choice_expected":true},{"wallet_id":"01a10e97-e388-78d2-a382-fc534affb96e","successor_user_id":"01a10e92-3627-702f-a813-a5d792dd4365","reason":"chosen","choice_expected":true}]},"meta":{}}""")

    /** Live `d_post_200_replace_choice`. */
    val requestReplaceChoice = Captured(200, listOf(), """{"data":{"state":"pending_deletion","deletion_scheduled_for":"2026-11-05T00:23:26.934042Z","successors":[{"wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","successor_user_id":"01a10e92-3627-702f-a813-a5d792dd4365","reason":"only_member","choice_expected":false},{"wallet_id":"01a10e97-a75f-7d07-a16c-84044a9b88ef","successor_user_id":"01a10e97-5def-7013-8854-a66555c62d0d","reason":"chosen","choice_expected":true},{"wallet_id":"01a10e97-e388-78d2-a382-fc534affb96e","successor_user_id":"01a10e97-5f2b-705f-b53f-ae547fadac56","reason":"oldest_viewer","choice_expected":true}]},"meta":{}}""")

    /** Live `d_post_200_no_shared`. */
    val requestNoSharedWallets = Captured(200, listOf(), """{"data":{"state":"pending_deletion","deletion_scheduled_for":"2026-11-05T00:24:42.511335Z","successors":[]},"meta":{}}""")

    /** Live `d_post_422_missing_password`. */
    val missingPassword = Captured(422, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10e98-0402-73ed-b385-91719bb70846","errors":[{"pointer":"\/password","code":"REQUIRED","detail":"This field is required."}]}""")

    /** Live `d_post_422_successors_before_password`. */
    val successorsBeforePassword = Captured(422, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10e98-0457-737f-9d72-0ccf9c86d4c1","errors":[{"pointer":"\/successors\/0\/member_user_id","code":"INVALID_ENUM_VALUE","detail":"This value is not one of the allowed options."}]}""")

    /** Live `d_post_401_wrong_password`. */
    val wrongPassword = Captured(401, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/invalid-credentials","title":"Invalid credentials","status":401,"detail":"The email address or password is incorrect.","code":"INVALID_CREDENTIALS","request_id":"01a10e98-04b3-71e5-94aa-3c7946029c9f"}""")

    /** Live `d_post_422_successor_not_member`. */
    val successorNotMember = Captured(422, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10e98-05f7-7054-a8a9-582d9a484313","errors":[{"pointer":"\/successors\/0\/member_user_id","code":"INVALID_ENUM_VALUE","detail":"This value is not one of the allowed options."}]}""")

    /** Live `d_post_422_wallet_not_owned`. */
    val walletNotOwned = Captured(422, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10e98-0658-7160-adfe-a6cb4b19312d","errors":[{"pointer":"\/successors\/0\/wallet_id","code":"INVALID_ENUM_VALUE","detail":"This value is not one of the allowed options."},{"pointer":"\/successors\/0\/member_user_id","code":"INVALID_ENUM_VALUE","detail":"This value is not one of the allowed options."}]}""")

    /** Live `d_post_422_duplicate_wallet`. */
    val duplicateWallet = Captured(422, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10e98-2c2e-72f9-a0cb-d678badd0589","errors":[{"pointer":"\/successors\/1\/wallet_id","code":"INVALID_ENUM_VALUE","detail":"This value is not one of the allowed options."}]}""")

    /** Live `d_delete_204_cancel`. */
    val cancelled = Captured(204, listOf(), """""")

    /** Live `d_delete_when_active`. */
    val cancelWhenActive = Captured(204, listOf(), """""")

    /** Live `a_login_while_purging`. */
    val signInWhilePurging = Captured(401, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/invalid-credentials","title":"Invalid credentials","status":401,"detail":"The email address or password is incorrect.","code":"INVALID_CREDENTIALS","request_id":"01a10e9b-0020-729d-8b23-70aa2e4b0d9c"}""")

    /** Live `a_login_unknown_for_comparison`. */
    val signInUnknownForComparison = Captured(401, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/invalid-credentials","title":"Invalid credentials","status":401,"detail":"The email address or password is incorrect.","code":"INVALID_CREDENTIALS","request_id":"01a10e9b-0261-7386-a51f-aaceec040fb6"}""")
}
