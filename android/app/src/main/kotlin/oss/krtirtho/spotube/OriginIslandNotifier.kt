package oss.krtirtho.spotube

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.ArrayList

/**
 * Experimental native OriginOS 6 island integration.
 *
 * Uses a normal Android notification carrying Vivo's undocumented SuperX
 * bundles. Android MediaSession / audio_service remains the sole source of
 * actual playback controls and seeking. Other Android vendors are unaffected.
 *
 * Protocol reference:
 * https://github.com/cameleonnbss/originos-toolkit/blob/main/docs/ORIGINISLAND.md
 */
class OriginIslandNotifier(private val activity: Activity) {
    companion object {
        const val PERMISSION_REQUEST = 6206
        private const val CHANNEL_ID = "spotube_origin_island"
        private const val NOTIFICATION_ID = 6206
        private const val SCENE = "TRAIN"
        private const val ACCENT = -0x83b301 // ARGB 0xFF7C4CFF
    }

    private data class Song(
        val title: String,
        val artist: String,
        val positionMs: Long,
        val durationMs: Long,
        val playing: Boolean,
    )

    private val supported: Boolean
        get() = Build.VERSION.SDK_INT >= 36 &&
            listOf(Build.MANUFACTURER, Build.BRAND).any {
                it.contains("vivo", ignoreCase = true) ||
                    it.contains("iqoo", ignoreCase = true)
            }

    private val notificationManager: NotificationManager?
        get() = activity.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private var pending: Song? = null
    private var permissionRequested = false
    private var sceneRegistered = false

    fun update(
        title: String,
        artist: String,
        positionMs: Long,
        durationMs: Long,
        playing: Boolean,
    ) {
        if (!supported) return
        if (title.isBlank()) {
            clear()
            return
        }
        pending = Song(
            title = title,
            artist = artist,
            positionMs = positionMs.coerceAtLeast(0),
            durationMs = durationMs.coerceAtLeast(0),
            playing = playing,
        )

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Only vivo Android 16 users will ever see this request. Ordinary
            // MediaSession notifications do not rely on this permission.
            if (!permissionRequested) {
                permissionRequested = true
                runCatching {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        PERMISSION_REQUEST,
                    )
                }
            }
            return
        }
        pending?.let(::post)
    }

    fun onNotificationPermissionGranted() {
        if (supported) pending?.let(::post)
    }

    fun clear() {
        pending = null
        if (!supported) return
        val manager = notificationManager ?: return
        runCatching {
            val endExtras = Bundle().apply {
                putInt("notification.superx.operation", 2)
            }
            manager.notify(
                NOTIFICATION_ID,
                Notification.Builder(activity, CHANNEL_ID)
                    .setSmallIcon(activity.applicationInfo.icon)
                    .addExtras(endExtras)
                    .build(),
            )
        }
        manager.cancel(NOTIFICATION_ID)
    }

    private fun post(song: Song) {
        val manager = notificationManager ?: return
        runCatching {
            ensureChannel(manager)
            registerScene(manager)

            val icon = Icon.createWithResource(activity, activity.applicationInfo.icon)
            val extras = createExtras(song, icon)
            val launchIntent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            val pendingIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    activity,
                    0,
                    it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }

            val notification = Notification.Builder(activity, CHANNEL_ID)
                .setSmallIcon(activity.applicationInfo.icon)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setContentIntent(pendingIntent)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(song.playing)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .addExtras(extras)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
        // Best-effort only: unsupported/modified ROMs must not crash playback.
    }

    private fun ensureChannel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Spotube Origin Island",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Native music status on compatible Vivo OriginOS devices"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun registerScene(manager: NotificationManager) {
        if (sceneRegistered) return
        sceneRegistered = true
        runCatching {
            val method = NotificationManager::class.java.getMethod(
                "setSuperXInfosSceneList",
                MutableList::class.java,
                MutableList::class.java,
                MutableList::class.java,
                MutableList::class.java,
            )
            method.invoke(
                manager,
                arrayListOf(SCENE),
                arrayListOf("true"),
                arrayListOf(activity.packageName),
                arrayListOf("true"),
            )
        }
    }

    private fun createExtras(song: Song, icon: Icon): Bundle {
        val progress = if (song.durationMs > 0L) {
            ((song.positionMs.coerceAtMost(song.durationMs) * 100) /
                song.durationMs).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val hasProgress = song.durationMs > 0 && progress in 1..99

        val baseInfos = Bundle().apply {
            putCharSequence("notification.superx.baseInfos.title", song.title)
            putCharSequence("notification.superx.baseInfos.content", song.artist)
            putParcelable("notification.superx.baseInfos.icon", icon)
        }

        val capsule = Bundle().apply {
            putInt("notification.superx.capsule.state", 1)
            putCharSequence("notification.superx.capsule.content", song.title)
            putParcelable("notification.superx.capsule.icon", icon)
        }

        val infos = Bundle().apply {
            if (hasProgress) {
                putInt("notification.superx.infos.progress", progress)
                putInt("notification.superx.infos.progressColor", ACCENT)
                putParcelableArrayList(
                    "notification.superx.infos.nodeIcon",
                    ArrayList(listOf(icon, icon)),
                )
                putParcelable("notification.superx.infos.indicatorIcon", icon)
                putInt("notification.superx.infos.indicatorLoc", 1)
            } else {
                putString("notification.superx.infos.describe", song.title)
                putString("notification.superx.infos.coreInfo", song.artist)
                putParcelable("notification.superx.infos.image", icon)
            }
        }

        val shortInfos = Bundle().apply {
            putString("notification.superx.shortInfos.describeShort", song.title)
            putString("notification.superx.shortInfos.coreInfoShort", song.artist)
            putParcelable("notification.superx.shortInfos.image", icon)
        }

        val leftInfo = Bundle().apply {
            putString("island.superx.leftInfo.content", song.title)
            putParcelable("island.superx.leftInfo.icon", icon)
        }

        val rightInfo = Bundle().apply {
            if (hasProgress) {
                putInt("island.superx.rightInfo.progressValue", progress)
                putInt("island.superx.rightInfo.progressState", 0)
                putInt("island.superx.rightInfo.progressColor", ACCENT)
            } else {
                putString("island.superx.rightInfo.content", song.artist)
                putParcelable("island.superx.rightInfo.icon", icon)
            }
        }

        val island = Bundle().apply {
            putInt("island.superx.leftTemplate", 1)
            putInt("island.superx.rightTemplate", if (hasProgress) 2 else 4)
            putBundle("island.superx.leftInfo", leftInfo)
            putBundle("island.superx.rightInfo", rightInfo)
        }

        return Bundle().apply {
            putInt("notification.superx.operation", 0)
            putBoolean("notification.superx.showNotify", true)
            putInt("notification.superx.template", if (hasProgress) 2 else 1)
            putString("notification.superx.scene", SCENE)
            putInt("notification.superx.changedRecord", 0)
            putBundle("notification.superx.baseInfos", baseInfos)
            putBundle("notification.superx.capsule", capsule)
            putBundle("notification.superx.infos", infos)
            putBundle("notification.superx.shortInfos", shortInfos)
            putBundle("notification.superx.island", island)
        }
    }
}
