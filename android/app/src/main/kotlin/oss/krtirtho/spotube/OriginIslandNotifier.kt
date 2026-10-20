package oss.krtirtho.spotube

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Experimental OriginOS 6 music status, based on CardBox's working
 * Android 16 promoted ongoing / ProgressStyle notification implementation.
 *
 * This is a separate status chip. The audio_service MediaSession and its
 * MediaStyle notification remain responsible for playback, seeking, lock
 * screen control and audio focus.
 *
 * Vivo can still restrict which apps appear in its native music island.
 */
class OriginIslandNotifier(private val activity: Activity) {
    companion object {
        const val PERMISSION_REQUEST = 6206
        private const val TAG = "SpotubeOriginIsland"
        private const val CHANNEL_ID = "spotube_origin_island"
        private const val NOTIFICATION_ID = 6206
        private const val REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        private const val BLUE_VIOLET = 0xFF5D58D7.toInt()
    }

    private data class Song(
        val title: String,
        val artist: String,
        val positionMs: Long,
        val durationMs: Long,
    )

    private val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= 36 &&
            listOf(Build.BRAND, Build.MANUFACTURER).any {
                it.contains("vivo", ignoreCase = true) ||
                    it.contains("iqoo", ignoreCase = true)
            }

    private val notificationManager: NotificationManager?
        get() = activity.getSystemService(NotificationManager::class.java)

    private var pending: Song? = null
    private var permissionRequested = false

    fun update(
        title: String,
        artist: String,
        positionMs: Long,
        durationMs: Long,
        playing: Boolean,
    ) {
        if (!isSupported) return
        if (!playing || title.isBlank()) {
            clear()
            return
        }

        pending = Song(
            title = title,
            artist = artist,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
        )

        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (!permissionRequested) {
                permissionRequested = true
                runCatching {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        PERMISSION_REQUEST,
                    )
                }.onFailure {
                    Log.w(TAG, "Cannot request notifications from background", it)
                }
            }
            return
        }

        pending?.let(::post)
    }

    fun onNotificationPermissionGranted() {
        if (isSupported) pending?.let(::post)
    }

    fun clear() {
        pending = null
        if (!isSupported) return
        runCatching { notificationManager?.cancel(NOTIFICATION_ID) }
    }

    private fun post(song: Song) {
        val manager = notificationManager ?: return
        runCatching {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Spotube music status",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Native OriginOS music status while audio plays"
                    setSound(null, null)
                    enableVibration(false)
                },
            )

            val progress = if (song.durationMs > 0) {
                ((song.positionMs.coerceAtMost(song.durationMs) * 100L) /
                    song.durationMs).toInt().coerceIn(0, 100)
            } else {
                0
            }
            val remainingSeconds =
                ((song.durationMs - song.positionMs).coerceAtLeast(0L) / 1000L)
            val timeLabel = "%d:%02d".format(
                remainingSeconds / 60L,
                remainingSeconds % 60L,
            )

            val style = Notification.ProgressStyle()
                .setStyledByProgress(true)
                .setProgressTrackerIcon(
                    Icon.createWithResource(activity, android.R.drawable.ic_media_play),
                )
                .setProgressEndIcon(
                    Icon.createWithResource(activity, android.R.drawable.ic_media_next),
                )
                .addProgressSegment(
                    Notification.ProgressStyle.Segment(100).setColor(BLUE_VIOLET),
                )
            if (song.durationMs > 0) {
                style.setProgress(progress)
            } else {
                style.setProgressIndeterminate(true)
            }

            val launchIntent =
                activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            val contentIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    activity,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

            val builder = Notification.Builder(activity, CHANNEL_ID)
                .setSmallIcon(activity.applicationInfo.icon)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setSubText("Spotube")
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setSilent(true)
                .setColor(Color.rgb(93, 88, 215))
                .setStyle(style)
                .setShortCriticalText(timeLabel.take(7))
                .addExtras(android.os.Bundle().apply {
                    putBoolean(REQUEST_PROMOTED_ONGOING, true)
                })
                .addAction(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    mediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PREVIOUS, 1),
                )
                .addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    mediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 2),
                )
                .addAction(
                    android.R.drawable.ic_media_next,
                    "Next",
                    mediaButtonIntent(KeyEvent.KEYCODE_MEDIA_NEXT, 3),
                )

            val notification = builder.build()
            val characteristics = notification.hasPromotableCharacteristics()
            val promotionAllowed = manager.canPostPromotedNotifications()
            Log.d(
                TAG,
                "promotable=$characteristics promotionAllowed=$promotionAllowed",
            )
            if (characteristics && promotionAllowed) {
                manager.notify(NOTIFICATION_ID, notification)
            } else {
                // Never clutter the shade with a duplicate ordinary
                // notification when the ROM declines promotion.
                manager.cancel(NOTIFICATION_ID)
            }
        }.onFailure {
            Log.w(TAG, "Origin Island status is unavailable on this ROM", it)
        }
    }

    private fun mediaButtonIntent(keyCode: Int, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            setClassName(
                activity.packageName,
                "com.ryanheise.audioservice.MediaButtonReceiver",
            )
            putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, keyCode),
            )
        }
        return PendingIntent.getBroadcast(
            activity,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
