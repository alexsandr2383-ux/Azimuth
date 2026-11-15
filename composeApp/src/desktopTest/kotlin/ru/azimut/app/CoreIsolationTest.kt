package ru.azimut.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Guards the architecture rule: the core package must not depend on Compose. */
class CoreIsolationTest {
    @Test
    fun coreDoesNotImportCompose() {
        val roots = listOf("commonMain", "desktopMain", "androidMain")
            .map { File("src/$it/kotlin/ru/azimut/app/core") }
            .filter { it.exists() }
        assertTrue(roots.isNotEmpty(), "core sources not found from ${File(".").absolutePath}")
        val offenders = roots.flatMap { r -> r.walkTopDown().filter { it.extension == "kt" }.toList() }
            .filter { f -> f.readLines().any { it.trim().startsWith("import androidx.compose") } }
            .map { it.path }
        assertTrue(offenders.isEmpty(), "core must not import Compose: $offenders")
    }
}
