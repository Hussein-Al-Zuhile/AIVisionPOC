package com.tatweer.aivisionpoc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val variant: ModelVariant, val progress: Float) : DownloadState()
    data class Failed(val variant: ModelVariant, val message: String) : DownloadState()
    data class Done(val variant: ModelVariant) : DownloadState()
}

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val variantName = intent?.getStringExtra(EXTRA_VARIANT)
            ?: run { stopSelf(); return START_NOT_STICKY }
        val variant = ModelVariant.valueOf(variantName)

        ensureChannel()
        startForeground(NOTIF_ID, buildNotification(variant, 0f))

        job = scope.launch {
            try {
                _state.value = DownloadState.Downloading(variant, 0f)
                AiHelper.downloadModel(applicationContext, variant) { progress ->
                    _state.value = DownloadState.Downloading(variant, progress)
                    notificationManager.notify(NOTIF_ID, buildNotification(variant, progress))
                }
                _state.value = DownloadState.Done(variant)
            } catch (e: CancellationException) {
                _state.value = DownloadState.Idle
                throw e
            } catch (e: Exception) {
                _state.value = DownloadState.Failed(variant, e.message ?: "Download failed")
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Model Downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(variant: ModelVariant, progress: Float) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading ${variant.modelFile.substringAfterLast('/')}")
            .setContentText(if (progress > 0f) "${(progress * 100).toInt()}%" else "Connecting…")
            .setProgress(100, (progress * 100).toInt(), progress == 0f)
            .setOngoing(true)
            .setSilent(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "model_download"
        private const val NOTIF_ID = 1
        private const val EXTRA_VARIANT = "variant"

        private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
        val state: StateFlow<DownloadState> = _state.asStateFlow()

        fun start(context: Context, variant: ModelVariant) {
            context.startForegroundService(
                Intent(context, DownloadService::class.java).putExtra(EXTRA_VARIANT, variant.name)
            )
        }

        fun cancel(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
            _state.value = DownloadState.Idle
        }
    }
}
