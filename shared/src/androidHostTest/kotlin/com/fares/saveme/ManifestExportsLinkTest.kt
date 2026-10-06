package com.fares.saveme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The app claims only its invitation path, so an export's signed link opens in the browser, never in the app (spec 016 R-02). */
class ManifestExportsLinkTest {

    private val manifests = listOf("main", "dev", "staging", "prod", "debug").map { File("../androidApp/src/$it/AndroidManifest.xml") }.filter { it.exists() }

    @Test
    fun noIntentFilterCoversTheExportsPath() {
        assertTrue(manifests.isNotEmpty(), "run from :shared: ${File("..").absolutePath}")
        manifests.forEach { manifest ->
            val text = manifest.readText()
            assertTrue(!text.contains("/exports"), "${manifest.path} mentions /exports")
            Regex("""android:(?:path|pathPrefix|pathPattern|pathAdvancedPattern)="([^"]*)"""").findAll(text).forEach { match ->
                val path = match.groupValues[1]
                assertTrue(!Regex(path.replace(".*", ".*")).matches("/exports/abc/file") || path == "", "${manifest.path} path $path covers the export file route")
            }
        }
    }
}
