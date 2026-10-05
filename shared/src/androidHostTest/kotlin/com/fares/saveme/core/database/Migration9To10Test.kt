package com.fares.saveme.core.database

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.fares.saveme.feature.sharing.data.local.InvitationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * v9 → v10 (spec 013, data-model §5, FR-030): the `invitation` table is added and every wallet scope's feed cursor, read stamp
 * and integer position is forgotten once; the person's own scope and every other row are kept. The v9 file is built from the
 * exported `schemas/…/9.json` (every table and index), so Room validates the result against v10.
 */
@RunWith(RobolectricTestRunner::class)
class Migration9To10Test {

    private val context = RuntimeEnvironment.getApplication()
    private val schemas = "schemas/com.fares.saveme.core.database.AppDatabase"

    private fun v9Database(): File {
        val schema = Json.parseToJsonElement(File("$schemas/9.json").readText()).jsonObject.getValue("database").jsonObject
        val file = File(context.cacheDir, "migration910-${System.nanoTime()}.db")
        AndroidSQLiteDriver().open(file.path).run {
            for (entity in schema.getValue("entities").jsonArray.map { it.jsonObject }) {
                val table = entity.getValue("tableName").jsonPrimitive.content
                execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                (entity["indices"] as? JsonArray)?.map { it.jsonObject }?.forEach { index ->
                    execSQL(index.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            execSQL("INSERT INTO profile VALUES ('u1','p1',3,'Sara','en','Africa/Cairo','EGP',0,25,NULL,'2026-10-01T10:00:00Z')")
            execSQL(
                "INSERT INTO wallet VALUES ('w1','u1','cash','Pocket','EGP',100,100,NULL,NULL,NULL,0,'owner',1,1," +
                    "'2026-10-01T10:00:00Z','2026-10-01T10:00:00Z','2026-10-01T10:00:00Z')",
            )
            execSQL("INSERT INTO wallet_member VALUES ('m1','w1','u1','Sara','owner',1,'2026-10-01T10:00:00Z')")
            execSQL(
                "INSERT INTO budget VALUES ('b1','u1',NULL,'monthly',600000,'EGP',NULL,1,1," +
                    "'2026-10-01T10:00:00Z','2026-10-01T10:00:00Z','2026-10-01T10:00:00Z')",
            )
            execSQL(
                "INSERT INTO pending_change VALUES ('wallet','w1','u1','wallet','w1',1,'{}',0,0,0,1,'Waiting',0,NULL,NULL,NULL,'2026-10-01T10:00:00Z')",
            )
            execSQL("INSERT INTO sync_scope VALUES ('profile','2026-10-01T10:00:00Z',NULL,NULL,NULL,NULL)")
            execSQL("INSERT INTO sync_scope VALUES ('user:u1','2026-10-01T10:00:00Z','2026-10-01T09:00:00Z','user-cursor','2026-10-02T08:00:00.000Z',41)")
            execSQL("INSERT INTO sync_scope VALUES ('wallet:w1','2026-10-01T10:00:00Z','2026-10-01T09:30:00Z','wallet-cursor','2026-10-02T08:30:00.000Z',812)")
            execSQL("INSERT INTO sync_scope VALUES ('wallet:w2','2026-10-01T10:00:00Z',NULL,'wallet-two','2026-10-02T08:31:00.000Z',133)")
            execSQL("PRAGMA user_version = 9")
            close()
        }
        return file
    }

    private fun open(file: File) = Room.databaseBuilder<AppDatabase>(context, file.path)
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(MIGRATION_9_10)
        .build()

    @Test
    fun everyWalletScopesPlaceIsForgottenAndTheUserScopeKeepsItsOwn() = runTest {
        val db = open(v9Database())
        val dao = db.syncScopeDao()
        for (scope in listOf("wallet:w1", "wallet:w2")) {
            val row = dao.observe(scope).first()!!
            assertNull(scope, row.feedCursor)
            assertNull(scope, row.feedReadAt)
            assertNull(scope, row.feedPosition)
        }
        assertEquals("the push stamp of a wallet scope is kept", "2026-10-01T09:30:00Z", dao.observe("wallet:w1").first()!!.lastPushedAt)
        val user = dao.observe("user:u1").first()!!
        assertEquals("user-cursor", user.feedCursor)
        assertEquals("2026-10-02T08:00:00.000Z", user.feedReadAt)
        assertEquals(41L, user.feedPosition)
        assertEquals(FeedPlace("user-cursor", 41L), dao.feedPlace("user:u1"))
        assertNotNull("a scope that is not a feed scope is untouched", dao.observe("profile").first())
        db.close()
    }

    @Test
    fun everyOtherRowIsKeptIncludingTheWaitingChange() = runTest {
        val db = open(v9Database())
        assertEquals("Sara", db.profileDao().get()!!.displayName)
        assertEquals("Pocket", db.walletDao().get("w1")!!.name)
        assertEquals("owner", db.walletMemberDao().get("w1").single().role)
        assertEquals(600_000L, db.budgetDao().get("b1")!!.amountMinor)
        assertNotNull(db.pendingChangeDao().get("wallet", "w1"))
        db.close()
    }

    @Test
    fun theInvitationTableExistsWithItsColumnsAndRoundTripsRows() = runTest {
        val db = open(v9Database())
        val placeholder = InvitationEntity(
            "i1", "u1", "w1", "editor", "email", "karim@example.com", "u1", "pending", null, 0, 0,
            "2026-10-02T10:00:00Z", "2026-10-02T10:00:00Z", "2026-10-02T10:00:00Z",
        )
        db.invitationDao().upsert(placeholder)
        db.invitationDao().upsert(placeholder.copy(id = "i2", channel = "link", inviteeEmail = null, version = 1, expiresAt = "2026-10-09T10:00:00Z"))
        assertEquals(listOf("i1", "i2"), db.invitationDao().observePending("u1", "w1").first().map { it.id })
        assertEquals(placeholder, db.invitationDao().get("i1"))
        db.close()
    }

    @Test
    fun theExportedSchemaHasTheTableAndTheIndexAndNothingElseChanged() {
        fun tables(version: Int): Map<String, JsonObject> = Json.parseToJsonElement(File("$schemas/$version.json").readText())
            .jsonObject.getValue("database").jsonObject.getValue("entities").jsonArray.map { it.jsonObject }
            .associateBy { it.getValue("tableName").jsonPrimitive.content }
        val before = tables(9)
        val after = tables(10)
        assertEquals(before.keys + "invitation", after.keys)
        before.keys.forEach { assertEquals(it, before[it], after[it]) }
        val invitation = after.getValue("invitation")
        assertEquals(
            listOf(
                "id", "account_id", "wallet_id", "role", "channel", "invitee_email", "invited_by", "status", "expires_at", "version",
                "change_seq", "created_at", "updated_at", "refreshed_at",
            ),
            invitation.getValue("fields").jsonArray.map { it.jsonObject.getValue("columnName").jsonPrimitive.content },
        )
        assertEquals(
            listOf("index_invitation_account_id_wallet_id_status"),
            invitation.getValue("indices").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content },
        )
        assertTrue(File("$schemas/10.json").exists())
    }

    @Test
    fun noColumnCanHoldAnInvitationSecretOrALink() {
        val columns = Json.parseToJsonElement(File("$schemas/10.json").readText()).jsonObject.getValue("database").jsonObject
            .getValue("entities").jsonArray.map { it.jsonObject }.first { it.getValue("tableName").jsonPrimitive.content == "invitation" }
            .getValue("fields").jsonArray.map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
        assertTrue(columns.toString(), columns.none { "token" in it || "url" in it || "link" in it || "secret" in it })
    }
}
