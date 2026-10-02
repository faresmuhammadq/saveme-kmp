package com.fares.saveme.core.sync

/** The examples of `saveme-backend/docs/openapi/paths/sync.yaml` (`saveme-backend@6b8e290`), as JSON. */
object OpenApiSyncExamples {
    const val USER_FEED_PAGE = """
{"data":[
 {"type":"wallet_member","record":{"id":"0192f7a4-0000-7000-8000-0000000000a1","wallet_id":"0192f79e-0000-7000-8000-000000000002","version":1,"change_seq":4,
   "created_at":"2026-10-04T09:00:00.000000Z","updated_at":"2026-10-04T09:00:00.000000Z","deleted_at":null,
   "member_user_id":"0192f700-0000-7000-8000-000000000001","display_name":"Sara","role":"editor"}},
 {"type":"category","record":{"id":"0192f7a1-0000-7000-8000-0000000000c1","user_id":"0192f700-0000-7000-8000-000000000001","version":3,"change_seq":58,
   "created_at":"2026-09-20T10:00:00.000000Z","updated_at":"2026-10-04T09:05:00.000000Z","deleted_at":null,"kind":"expense","name":"Groceries",
   "icon":"cart","color":"#2E7D32","archived_at":null,"in_use":true}}],
 "meta":{"next_cursor":null,"has_more":false,"limit":50,"sync_cursor":"eyJ2IjoxLCJmIjoidSIsInMiOiIwMTkyZjcwMCIsInAiOjU5fQ.c2lnbmF0dXJlMTZi","an_unknown_member":1}}
"""

    const val WALLET_FEED_PAGE = """
{"data":[
 {"type":"transaction","record":{"id":"0192f7b0-0000-7000-8000-0000000000e1","wallet_id":"0192f79e-0000-7000-8000-000000000001","version":1,"change_seq":812,
   "created_at":"2026-10-04T08:00:00.000000Z","updated_at":"2026-10-04T08:00:00.000000Z","deleted_at":null,"kind":"transfer","date":"2026-10-04",
   "amount_minor":50000,"currency":"EGP","category_id":null,"counter_wallet_id":"0192f79e-0000-7000-8000-000000000002","description":"Household share",
   "memo":null,"counterparty":null,"cleared":false,"created_by":"0192f700-0000-7000-8000-000000000001"}},
 {"type":"transaction","record":{"id":"0192f7b0-0000-7000-8000-0000000000e2","wallet_id":"0192f79e-0000-7000-8000-000000000002","version":3,"change_seq":133,
   "created_at":"2026-10-01T18:20:00.000000Z","updated_at":"2026-10-04T08:10:00.000000Z","deleted_at":"2026-10-04T08:10:00.000000Z","kind":"expense","date":"2026-10-01",
   "amount_minor":25000,"currency":"EGP","category_id":"0192f7a1-0000-7000-8000-0000000000c1","counter_wallet_id":null,"description":"Carrefour",
   "memo":null,"counterparty":null,"cleared":false,"created_by":"0192f700-0000-7000-8000-000000000003"}}],
 "meta":{"next_cursor":"eyJ2IjoxLCJmIjoidyIsInMiOiIwMTkyZjc5ZSIsInAiOjEzM30.c2lnbmF0dXJlMTZi","has_more":true,"limit":2,
         "sync_cursor":"eyJ2IjoxLCJmIjoidyIsInMiOiIwMTkyZjc5ZSIsInAiOjEzM30.c2lnbmF0dXJlMTZi"}}
"""

    /** Two applied, one clash (the record was deleted on another device), one refused (not found). */
    const val MIXED_RESULTS = """
{"data":[
 {"type":"category","id":"0192f7a1-0000-7000-8000-0000000000c2","outcome":"applied","status":201,
  "record":{"id":"0192f7a1-0000-7000-8000-0000000000c2","user_id":"0192f700-0000-7000-8000-000000000001","version":1,"change_seq":60,
   "created_at":"2026-10-04T10:00:00.000000Z","updated_at":"2026-10-04T10:00:00.000000Z","deleted_at":null,"kind":"expense","name":"Pharmacy",
   "icon":"pill","color":"#C62828","archived_at":null,"in_use":false},"problem":null},
 {"type":"transaction","id":"0192f7b0-0000-7000-8000-0000000000e3","outcome":"applied","status":201,
  "record":{"id":"0192f7b0-0000-7000-8000-0000000000e3","wallet_id":"0192f79e-0000-7000-8000-000000000001","version":1,"change_seq":813,
   "created_at":"2026-10-04T10:00:00.100000Z","updated_at":"2026-10-04T10:00:00.100000Z","deleted_at":null,"kind":"expense","date":"2026-10-04",
   "amount_minor":12500,"currency":"EGP","category_id":"0192f7a1-0000-7000-8000-0000000000c2","counter_wallet_id":null,"description":"Seif pharmacy",
   "memo":null,"counterparty":null,"cleared":false,"created_by":"0192f700-0000-7000-8000-000000000001"},"problem":null},
 {"type":"transaction","id":"0192f7b0-0000-7000-8000-0000000000e2","outcome":"conflict","status":409,"record":null,
  "problem":{"type":"https://api.example/problems/version-conflict","title":"Version conflict","status":409,"detail":"This record changed on another device.",
   "code":"VERSION_CONFLICT","request_id":"0192f7c0-9999-7000-8000-00000000abcd",
   "current":{"id":"0192f7b0-0000-7000-8000-0000000000e2","wallet_id":"0192f79e-0000-7000-8000-000000000002","version":3,"change_seq":133,
    "created_at":"2026-10-01T18:20:00.000000Z","updated_at":"2026-10-04T08:10:00.000000Z","deleted_at":"2026-10-04T08:10:00.000000Z","kind":"expense",
    "date":"2026-10-01","amount_minor":25000,"currency":"EGP","category_id":"0192f7a1-0000-7000-8000-0000000000c1","counter_wallet_id":null,
    "description":"Carrefour","memo":null,"counterparty":null,"cleared":false,"created_by":"0192f700-0000-7000-8000-000000000003"}}},
 {"type":"transaction","id":"0192f7b0-0000-7000-8000-0000000000e4","outcome":"rejected","status":404,"record":null,
  "problem":{"type":"https://api.example/problems/not-found","title":"Not found","status":404,"detail":"Not found.","code":"NOT_FOUND",
   "request_id":"0192f7c0-9999-7000-8000-00000000abcd"}}],
 "meta":{}}
"""

    const val VALIDATION_REFUSAL = """
{"data":[
 {"type":"transaction","id":"0192f7b0-0000-7000-8000-0000000000e5","outcome":"rejected","status":422,"record":null,
  "problem":{"type":"https://api.example/problems/validation-failed","title":"Validation failed","status":422,"detail":"The request has 1 invalid field.",
   "code":"VALIDATION_FAILED","request_id":"0192f7c0-9999-7000-8000-00000000abce",
   "errors":[{"pointer":"/amount_minor","code":"AMOUNT_NOT_INTEGER","detail":"Amount must be an integer number of minor units."}]}}],
 "meta":{}}
"""
}
