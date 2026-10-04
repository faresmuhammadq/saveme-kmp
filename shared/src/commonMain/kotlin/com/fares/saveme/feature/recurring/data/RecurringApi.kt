package com.fares.saveme.feature.recurring.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.recurring.data.dto.OccurrenceDto
import com.fares.saveme.feature.recurring.data.dto.OccurrenceWriteRequest
import com.fares.saveme.feature.recurring.data.dto.RecurringRuleDto
import com.fares.saveme.feature.recurring.data.dto.RecurringRuleWriteRequest
import com.fares.saveme.feature.recurring.data.dto.RuleVersionOnlyRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * The recurring endpoints of recurring.yaml (contracts/recurring-api-client.md). All credentialed. The GET
 * endpoints are never called: the wallet feed carries both record types (spec 009).
 */
class RecurringApi(private val client: ApiClient) {

    /** Creates (version 0) or saves a rule; an identical replay is answered 200. */
    suspend fun putRule(walletId: String, id: String, request: RecurringRuleWriteRequest): ApiResult<RecurringRuleDto> = client.call {
        method = HttpMethod.Put
        url("wallets/$walletId/recurring-rules/$id")
        contentType(ContentType.Application.Json)
        setBody(request.toJson())
    }

    /** The version travels in a JSON body on DELETE; the answer is the rule's tombstone. */
    suspend fun deleteRule(walletId: String, id: String, version: Long): ApiResult<RecurringRuleDto> = client.call {
        method = HttpMethod.Delete
        url("wallets/$walletId/recurring-rules/$id")
        contentType(ContentType.Application.Json)
        setBody(RuleVersionOnlyRequest(version))
    }

    /** Confirm, skip, un-skip or edit one occurrence; never creates (ADR-0018). */
    suspend fun putOccurrence(walletId: String, id: String, request: OccurrenceWriteRequest): ApiResult<OccurrenceDto> = client.call {
        method = HttpMethod.Put
        url("wallets/$walletId/occurrences/$id")
        contentType(ContentType.Application.Json)
        setBody(request.toJson())
    }

    /** "Check for new dates": no body, long default timeout is enough (research R-11). */
    suspend fun materialise(walletId: String, ruleId: String): ApiResult<RecurringRuleDto> = client.call {
        method = HttpMethod.Post
        url("wallets/$walletId/recurring-rules/$ruleId/materialise")
    }
}
