package ru.azimut.app.core.platform

import java.awt.Toolkit

actual object PlatformAlert {
    actual fun offRoute() {
        runCatching { Toolkit.getDefaultToolkit().beep() }
    }
}
