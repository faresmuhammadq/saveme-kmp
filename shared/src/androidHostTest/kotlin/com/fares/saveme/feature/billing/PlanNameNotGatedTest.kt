package com.fares.saveme.feature.billing

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P-12.1, SC-003: capability decisions go through the typed gate, never the plan's name or id.
 * Only auth (which owns the snapshot), the Plan screen and the Plan screen's data source may
 * read the display-only plan fields.
 */
class PlanNameNotGatedTest {

    private val root = File("src/commonMain/kotlin/com/fares/saveme/feature")
    private val allowed = listOf("feature/auth/", "feature/billing/ui/", "feature/billing/domain/PlanOverview.kt")
    private val forbidden = listOf(Regex("""\.planId\b"""), Regex("""(?<!")\baccount\.plan\b"""))

    @Test
    fun noFeatureFileOutsideTheAllowedOnesReadsThePlanReference() {
        assertTrue(root.isDirectory, "run from the :shared module: ${root.absolutePath}")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> allowed.none { file.invariantSeparatorsPath.contains(it) } }
            .filter { file -> file.readLines().any { line -> forbidden.any { it.containsMatchIn(line) } } }
            .map { it.invariantSeparatorsPath.substringAfter("saveme/") }
            .toList()
        assertTrue(offenders.isEmpty(), "plan name or id read outside the allowed files: $offenders")
    }
}
