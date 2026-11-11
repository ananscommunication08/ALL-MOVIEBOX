package com.example.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.music.MusicDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground Service that keeps video and music downloads running smoothly
 * even when the user minimizes the app or turns off the screen.
 * Displays real-time download progress and speed in the notification shade,
 * and alerts when downloads finish.
 */
class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var observerJob: Job? = null

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val previouslyDownloadingIds = mutableSetOf<String>()
    private val completedNotifiedIds = mutableSetOf<String>()
    private var lastNotificationUpdateTime = 0L

    companion object {
        private const val TAG = "DownloadFGService"
        const val NOTIFICATION_ID = 9001
        const val CHANNEL_PROGRESS_ID = "media_download_progress_channel"
        const val CHANNEL_COMPLETED_ID = "media_download_completed_channel"

        const val ACTION_START = "com.example.action.START_DOWNLOAD_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_DOWNLOAD_SERVICE"

        fun start(context: Context) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting DownloadForegroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed stopping DownloadForegroundService: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        acquireWakeLock()

        // Initialize with pre-existing completed IDs so we only alert for fresh completions
        val initialDownloads = MovieDownloadManager.getInstance(applicationContext).downloads.value
        initialDownloads.filter { it.status == DownloadStatus.COMPLETED }.forEach {
            completedNotifiedIds.add(it.id)
        }

        // Must start foreground immediately upon service creation
        val placeholderNotification = buildProgressNotification(
            title = "Downloading media in background...",
            text = "Preparing download...",
            progress = 0,
            isIndeterminate = true
        )
        startInForeground(NOTIFICATION_ID, placeholderNotification)

        startObservingDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServiceGracefully()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        return START_STICKY
    }

    private fun startInForeground(id: Int, notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(id, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in startInForeground: ${e.message}")
            try {
                startForeground(id, notification)
            } catch (_: Exception) {}
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AllMovieBox:DownloadWakeLock").apply {
                setReferenceCounted(false)
                acquire(8 * 60 * 60 * 1000L) // 8-hour safety maximum
            }
            Log.d(TAG, "Acquired partial WakeLock for background downloading")
        } catch (e: Exception) {
            Log.e(TAG, "Failed acquiring WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Released partial WakeLock")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock: ${e.message}")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Low importance for continuous progress (no sound/vibrate spam)
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "Download Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for background movie and series downloads"
                setShowBadge(false)
            }

            // Default importance for completion alert (pleasant ding when ready)
            val completedChannel = NotificationChannel(
                CHANNEL_COMPLETED_ID,
                "Completed Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a download has completed and is ready to play"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(completedChannel)
        }
    }

    private fun startObservingDownloads() {
        observerJob?.cancel()
        val movieManager = MovieDownloadManager.getInstance(applicationContext)
        val musicManager = MusicDownloadManager.getInstance(applicationContext)

        observerJob = serviceScope.launch {
            combine(
                movieManager.downloads,
                musicManager.activeDownloads
            ) { movieDownloads, musicActive ->
                Pair(movieDownloads, musicActive)
            }.collect { (movieDownloads, musicActive) ->
                val activeMovies = movieDownloads.filter { it.status == DownloadStatus.DOWNLOADING }
                val completedMovies = movieDownloads.filter { it.status == DownloadStatus.COMPLETED }

                // Check for newly completed items that were previously downloading
                for (completed in completedMovies) {
                    if (previouslyDownloadingIds.contains(completed.id) && !completedNotifiedIds.contains(completed.id)) {
                        completedNotifiedIds.add(completed.id)
                        notifyDownloadComplete(completed.title, completed.quality)
                    }
                }

                // Update previously downloading set
                previouslyDownloadingIds.clear()
                activeMovies.forEach { previouslyDownloadingIds.add(it.id) }

                val hasActiveMovies = activeMovies.isNotEmpty()
                val hasActiveMusic = musicActive.isNotEmpty()

                if (hasActiveMovies || hasActiveMusic) {
                    val now = System.currentTimeMillis()
                    // Throttle notification updates to at most once per 600ms to avoid notification spam
                    if (now - lastNotificationUpdateTime >= 600) {
                        lastNotificationUpdateTime = now

                        if (hasActiveMovies) {
                            val activeCount = activeMovies.size
                            if (activeCount == 1) {
                                val item = activeMovies.first()
                                val progressPercent = (item.progress * 100).toInt().coerceIn(0, 100)
                                val speedText = item.speedFormatted.ifBlank { "Downloading..." }
                                val title = if (item.isSeries && (item.seasonNumber > 0 || item.episodeNumber > 0)) {
                                    "${item.title} (S${item.seasonNumber}E${item.episodeNumber})"
                                } else {
                                    item.title
                                }
                                val notif = buildProgressNotification(
                                    title = title,
                                    text = "${item.quality} • $progressPercent% ($speedText)",
                                    progress = progressPercent,
                                    isIndeterminate = item.totalBytes <= 0
                                )
                                notificationManager.notify(NOTIFICATION_ID, notif)
                            } else {
                                val first = activeMovies.first()
                                val avgProgress = (activeMovies.sumOf { (it.progress * 100).toInt() } / activeCount).coerceIn(0, 100)
                                val notif = buildProgressNotification(
                                    title = "Downloading $activeCount items",
                                    text = "${first.title} (${first.quality}) • $avgProgress%",
                                    progress = avgProgress,
                                    isIndeterminate = false
                                )
                                notificationManager.notify(NOTIFICATION_ID, notif)
                            }
                        } else {
                            // Only music downloading
                            val count = musicActive.size
                            val firstEntry = musicActive.entries.first()
                            val notif = buildProgressNotification(
                                title = if (count == 1) "Downloading Song" else "Downloading $count Songs",
                                text = "${firstEntry.value}% completed • Background download",
                                progress = firstEntry.value,
                                isIndeterminate = false
                            )
                            notificationManager.notify(NOTIFICATION_ID, notif)
                        }
                    }
                } else {
                    // No active downloads running
                    delay(2500) // Brief grace period before tearing down service
                    val recheckMovies = movieManager.downloads.value.any { it.status == DownloadStatus.DOWNLOADING }
                    val recheckMusic = musicManager.activeDownloads.value.isNotEmpty()
                    if (!recheckMovies && !recheckMusic) {
                        stopServiceGracefully()
                    }
                }
            }
        }
    }

    private fun buildProgressNotification(
        title: String,
        text: String,
        progress: Int,
        isIndeterminate: Boolean
    ): Notification {
        val openDownloadsIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_downloads", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            openDownloadsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_PROGRESS_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, isIndeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun notifyDownloadComplete(title: String, quality: String) {
        val openDownloadsIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_downloads", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1002,
            openDownloadsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val completeNotif = NotificationCompat.Builder(this, CHANNEL_COMPLETED_ID)
            .setContentTitle("Download Complete")
            .setContentText("$title ($quality) is saved and ready to watch")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(title.hashCode(), completeNotif)
    }

    private fun stopServiceGracefully() {
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        observerJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        Log.d(TAG, "DownloadForegroundService destroyed")
    }
}
