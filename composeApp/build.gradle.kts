import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Версия живёт в AppInfo.kt (commonMain) — единственный источник.
fun appInfo(name: String): String {
    val text = file("src/commonMain/kotlin/ru/azimut/app/AppInfo.kt").readText()
    return Regex("""const val $name\s*=\s*"?([^"\s]+)"?""").find(text)!!.groupValues[1]
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    val isMac = System.getProperty("os.name").startsWith("Mac")
    if (isMac) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
                implementation("org.maplibre.gl:android-sdk:11.8.0")
                implementation(files("libs/brouter-1.7.10-ro.jar"))
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
                implementation("org.xerial:sqlite-jdbc:3.46.1.3")
                implementation("com.fazecast:jSerialComm:2.11.0")
                implementation(files("libs/brouter-1.7.10-ro.jar"))
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

android {
    namespace = "ru.azimut.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.azimut.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appInfo("VERSION_CODE").toInt()
        versionName = appInfo("VERSION")
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].assets.srcDirs("src/androidMain/assets")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

compose.desktop {
    application {
        mainClass = "ru.azimut.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Azimuth"
            packageVersion = "0.3.4"
            description = "Azimuth - offline navigation for enduro and hiking"
            vendor = "Sklyarov Aleksandr Mikhailovich"
            // java.sql is needed by sqlite-jdbc (.mbtiles); jlink drops it otherwise.
            modules("java.sql")
            windows {
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
            }
        }
    }
}
