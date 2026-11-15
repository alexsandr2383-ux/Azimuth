package ru.azimut.app.ui.components

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** Desktop: no system share sheet — copy to the clipboard. */
actual object Share {
    actual fun shareText(text: String) {
        runCatching {
            Toolkit.getDefaultToolkit()
                .systemClipboard
                .setContents(StringSelection(text), null)
        }
    }
}
