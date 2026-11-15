package ru.azimut.app.ui.components

import android.content.Intent
import ru.azimut.app.core.platform.AndroidApp

actual object Share {
    actual fun shareText(text: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            AndroidApp.context.startActivity(
                Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
