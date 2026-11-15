package ru.azimut.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

val LocalLang = compositionLocalOf { "ru" }

@Composable
fun tr(ru: String, en: String): String = if (LocalLang.current == "en") en else ru
