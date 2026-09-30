package com.fares.saveme

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.navigation.DeepLinkBus
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val deepLinks: DeepLinkBus by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A restored activity already handled its link before the process died.
        if (savedInstanceState == null) forwardLink(intent)

        setContent {
            // Exposes Compose testTags as view resource ids so Maestro's `id:` selector sees them
            // (E2E flows under .maestro/). Dev builds only.
            Box(if (BuildConfig.DEBUG_TOOLS) Modifier.semantics { testTagsAsResourceId = true } else Modifier) {
                App()
            }
        }
    }

    // singleTask: a link tapped while the app is open arrives here instead of a new activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        forwardLink(intent)
    }

    /** Raw string only; AppNavHost parses it against this environment's link host (research R-04). */
    private fun forwardLink(intent: Intent?) {
        intent?.dataString?.let(deepLinks::submit)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
