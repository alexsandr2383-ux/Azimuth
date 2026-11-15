package ru.azimut.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.maplibre.android.MapLibre
import ru.azimut.app.core.platform.AndroidApp
import ru.azimut.app.core.platform.AppCommands
import ru.azimut.app.core.platform.PlatformLocation
import ru.azimut.app.core.platform.SystemBars
import ru.azimut.app.core.platform.applySystemBars

class MainActivity : ComponentActivity() {

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) PlatformLocation.startPreview()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+: a second runtime dialog for background location is never
                // shown — the grant lives only in app settings ("Allow all the time").
                runCatching {
                    startActivity(
                        Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", packageName, null),
                        ),
                    )
                }
            } else {
                backgroundPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SystemBars.activity = this
        AndroidApp.context = applicationContext
        MapLibre.getInstance(this)
        requestPermissions()
        handleIntent(intent)
        setContent { App() }
        applySystemBars(SystemBars.hide)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(AppCommands.EXTRA_WHERE_AM_I, false) == true) {
            AppCommands.requestWhereAmI()
        }
    }

    override fun onStart() {
        super.onStart()
        PlatformLocation.startPreview()
    }

    override fun onResume() {
        super.onResume()
        applySystemBars(SystemBars.hide)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars(SystemBars.hide)
    }

    override fun onStop() {
        PlatformLocation.stopPreview()
        super.onStop()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}
