package com.fares.saveme.feature.sharing

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import com.fares.saveme.feature.sharing.domain.LinkSharer

/**
 * The system share sheet and a clipboard copy marked sensitive so the system does not preview it (spec 013 R-17, FR-005).
 * The text carries a one-time link, so nothing here logs it.
 */
class AndroidLinkSharer(private val context: Context) : LinkSharer {

    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else EXTRA_IS_SENSITIVE_BELOW_33, true)
        }
        clipboard.setPrimaryClip(clip)
    }

    private companion object {
        const val EXTRA_IS_SENSITIVE_BELOW_33 = "android.content.extra.IS_SENSITIVE"
    }
}
