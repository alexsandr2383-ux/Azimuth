package ru.azimut.app.core.platform

/** Short attention signal (sound, and vibration where available) for navigation warnings. */
expect object PlatformAlert {
    fun offRoute()
}
