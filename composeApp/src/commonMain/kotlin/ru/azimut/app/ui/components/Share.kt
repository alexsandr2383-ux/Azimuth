package ru.azimut.app.ui.components

/**
 * System-level text sharing: an ACTION_SEND chooser on Android, clipboard on
 * desktop (where there is no universal share sheet).
 */
expect object Share {
    fun shareText(text: String)
}
