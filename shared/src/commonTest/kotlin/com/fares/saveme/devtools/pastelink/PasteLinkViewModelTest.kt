package com.fares.saveme.devtools.pastelink

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.AuthLinkParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasteLinkViewModelTest {

    private val parser = AuthLinkParser("localhost", 43871, "http")
    private fun vm() = PasteLinkViewModel(isAuthLink = { parser.parse(it) != null })

    @Test
    fun aValidLinkIsSubmittedTrimmed() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(PasteLinkIntent.TextChanged("  http://localhost:43871/verify-email?token=abc \n"))
            vm.onIntent(PasteLinkIntent.OpenClicked)
            assertEquals(PasteLinkEffect.SubmitLink("http://localhost:43871/verify-email?token=abc"), awaitItem())
        }
        assertFalse(vm.state.value.parseError)
    }

    @Test
    fun anythingElseShowsTheErrorUntilEdited() = runMviTest {
        val vm = vm()
        vm.onIntent(PasteLinkIntent.TextChanged("https://example.com"))
        vm.onIntent(PasteLinkIntent.OpenClicked)
        assertTrue(vm.state.value.parseError)
        vm.onIntent(PasteLinkIntent.TextChanged("x"))
        assertFalse(vm.state.value.parseError)
    }

    @Test
    fun neverPrintsThePastedLink() = runMviTest {
        val vm = vm()
        vm.onIntent(PasteLinkIntent.TextChanged("http://localhost:43871/reset-password?token=secret-x"))
        assertFalse("secret-x" in vm.state.value.toString())
        assertFalse("secret-x" in PasteLinkEffect.SubmitLink("http://localhost:43871/reset-password?token=secret-x").toString())
    }
}
