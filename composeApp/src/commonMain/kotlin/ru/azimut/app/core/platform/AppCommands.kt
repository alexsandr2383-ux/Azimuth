package ru.azimut.app.core.platform

import kotlinx.coroutines.flow.MutableStateFlow

/** One-shot commands from Android widgets/notifications to the Compose UI. */
object AppCommands {
    const val EXTRA_WHERE_AM_I = "ru.azimut.app.extra.WHERE_AM_I"

    val showMap = MutableStateFlow(0L)
    val locate = MutableStateFlow(0L)

    fun requestWhereAmI() {
        showMap.value += 1
        locate.value += 1
    }
}
