package com.fares.saveme.feature.notifications

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P-10 for notifications (spec 017): ui -> domain -> data; data imports no domain or ui; domain imports no Ktor, Compose, Room or
 * Android; core (including core.sync) imports nothing of the feature; no other feature imports its data or ui; other features are
 * reached only through their domain; and Firebase never enters `:shared` (SC-009, FR-045).
 */
class NotificationsLayeringTest {

    private val main = File("src/commonMain/kotlin/com/fares/saveme")
    private val notifications = File(main, "feature/notifications")
    private val base = "com.fares.saveme.feature."

    private fun imports(root: File): List<Pair<String, String>> = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file -> file.readLines().filter { it.startsWith("import ") }.map { file.name to it.removePrefix("import ").trim() } }
        .toList()

    @Test
    fun theSourceTreeIsWhereTheTestExpectsIt() = assertTrue(notifications.isDirectory, "run from :shared: ${notifications.absolutePath}")

    @Test
    fun dataImportsNoDomainAndNoUi() {
        val data = imports(File(notifications, "data"))
        assertTrue(data.isNotEmpty())
        val offenders = data.filter { (_, i) -> i.startsWith(base + "notifications.domain") || i.startsWith(base + "notifications.ui") }
        assertTrue(offenders.isEmpty(), "notifications data reaches up: $offenders")
        val foreign = data.filter { (_, i) -> i.startsWith(base) && !i.startsWith(base + "notifications.") }
        assertTrue(foreign.isEmpty(), "notifications data imports another feature: $foreign")
    }

    @Test
    fun domainImportsNoKtorComposeRoomOrAndroid() {
        val forbidden = listOf("io.ktor", "androidx.compose", "androidx.room", "android.", "androidx.lifecycle", "org.jetbrains.compose")
        val offenders = imports(File(notifications, "domain")).filter { (_, i) -> forbidden.any { i.startsWith(it) } }
        assertTrue(offenders.isEmpty(), "notifications domain imports a framework: $offenders")
    }

    @Test
    fun uiImportsNoData() {
        val offenders = imports(File(notifications, "ui")).filter { (_, i) -> i.startsWith(base + "notifications.data") }
        assertTrue(offenders.isEmpty(), "notifications ui imports data: $offenders")
    }

    @Test
    fun theFeatureReachesOtherFeaturesOnlyThroughTheirDomain() {
        val foreign = imports(notifications).filter { (_, i) -> i.startsWith(base) && !i.startsWith(base + "notifications.") }
            .filter { (_, i) -> !i.removePrefix(base).substringAfter('.').startsWith("domain") }
        assertTrue(foreign.isEmpty(), "notifications imports another feature's internals: $foreign")
    }

    @Test
    fun coreAndTheSyncCoreImportNothingOfNotifications() {
        val offenders = File(main, "core").let { imports(it) }.filter { (_, i) -> i.startsWith(base + "notifications") }
        assertTrue(offenders.isEmpty(), "core imports notifications: $offenders")
        val design = imports(File(main, "designsystem")).filter { (_, i) -> i.startsWith(base) }
        assertTrue(design.isEmpty(), "designsystem imports a feature: $design")
    }

    @Test
    fun noOtherFeatureImportsNotificationsDataOrUi() {
        val features = File(main, "feature").listFiles().orEmpty().filter { it.isDirectory && it.name != "notifications" }
        val offenders = features.flatMap { imports(it) }.filter { (_, i) -> i.startsWith(base + "notifications.data") || i.startsWith(base + "notifications.ui") }
        assertTrue(offenders.isEmpty(), "another feature imports notifications data or ui: $offenders")
    }

    @Test
    fun noFirebaseClassIsImportedAnywhereInSharedOrOutsideTheAndroidAppPushPackage() {
        val shared = File("src").walkTopDown().filter { it.isFile && it.extension == "kt" }
        val sharedOffenders = shared.filter { f -> f.readLines().any { it.trim().startsWith("import com.google.firebase") } }.map { it.name }.toList()
        assertTrue(sharedOffenders.isEmpty(), "shared imports Firebase: $sharedOffenders")
        val app = File("../androidApp/src")
        if (app.isDirectory) {
            val appOffenders = app.walkTopDown().filter { it.isFile && it.extension == "kt" }
                .filter { f -> f.readLines().any { it.trim().startsWith("import com.google.firebase") } }
                .filter { "/com/fares/saveme/push/" !in it.invariantSeparatorsPath }.map { it.name }.toList()
            assertTrue(appOffenders.isEmpty(), "Firebase outside androidApp/.../push/: $appOffenders")
        }
    }

    @Test
    fun noFirebaseDependencyIsDeclaredBeforeTheGatedPhase() {
        val files = listOf("../gradle/libs.versions.toml", "../build.gradle.kts", "../androidApp/build.gradle.kts", "build.gradle.kts").map { File(it) }.filter { it.exists() }
        assertTrue(files.size >= 3)
        val offenders = files.filter { f -> f.readText().let { "firebase" in it.lowercase() || "google-services" in it.lowercase() || "gms.google" in it.lowercase() } }.map { it.name }
        assertTrue(offenders.isEmpty(), "a build file mentions Firebase or Google Services: $offenders")
    }
}
