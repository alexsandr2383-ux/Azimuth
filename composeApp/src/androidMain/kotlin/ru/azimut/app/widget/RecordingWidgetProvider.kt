package ru.azimut.app.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import ru.azimut.app.MainActivity
import ru.azimut.app.R
import ru.azimut.app.core.location.RecordingControl
import ru.azimut.app.core.location.TrackRecorder
import ru.azimut.app.core.platform.AndroidApp
import ru.azimut.app.core.platform.AppCommands

class RecordingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(
                id,
                RemoteViews(context.packageName, R.layout.widget_recording).apply {
                    setOnClickPendingIntent(R.id.widget_record, broadcast(context, ACTION_RECORD, 1))
                    setOnClickPendingIntent(R.id.widget_where, openAppIntent(context, 2))
                },
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_RECORD) startRecording(context)
    }

    private fun startRecording(context: Context) {
        AndroidApp.context = context.applicationContext
        if (!hasLocationPermission(context)) {
            openApp(context)
            return
        }
        if (TrackRecorder.state.value.recording) {
            openApp(context)
            return
        }
        val started = runCatching { RecordingControl.startWithSavedSettings() }.isSuccess
        if (!started) {
            runCatching { TrackRecorder.stopAndSaveAsync() }
            openApp(context)
        }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun openApp(context: Context) {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        }
    }

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AppCommands.EXTRA_WHERE_AM_I, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, RecordingWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val ACTION_RECORD = "ru.azimut.app.widget.RECORD"
    }
}
