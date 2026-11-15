package ru.azimut.app.core.platform

/** Hides or shows the system navigation buttons (Android); no-op on desktop. */
expect fun applySystemBars(hide: Boolean)
