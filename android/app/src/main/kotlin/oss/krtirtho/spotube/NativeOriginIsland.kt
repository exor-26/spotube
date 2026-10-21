package oss.krtirtho.spotube

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import java.util.ArrayList

/**
 * OriginOS 6 collapsed music island, attached to the existing audio_service
 * MediaStyle notification. Never posts a different notification ID or a
 * separate shade card, and does not interfere with the MediaSession seekbar.
 *
 * Vivo's rhythm extras are derived from the publicly reverse-engineered
 * OriginIsland template 1 protocol (cameleonnbss/originos-toolkit).
 * The system renders the animation, expansion, and camera-cutout placement.
 */
internal class NativeOriginIsland(private val context: Context) {
    companion object {
        private const val TAG = "SpotubeOriginIsland"
        // audio_service 0.18.x's foreground MediaStyle notification ID.
        private const val MEDIA_NOTIFICATION_ID = 1124
        private const val SCENE = "TRAIN"
        private const val FALLBACK_COLOR = 0xFF6757D6.toInt()
    }

    private val supported: Boolean
        get() = Build.VERSION.SDK_INT >= 36 &&
            listOf(Build.BRAND, Build.MANUFACTURER).any {
                it.contains("vivo", ignoreCase = true) ||
                    it.contains("iqoo", ignoreCase = true)
            }

    private val manager: NotificationManager?
        get() = context.getSystemService(NotificationManager::class.java)

    private var sceneRegistrationAttempted = false

    fun update(title: String, artist: String, playing: Boolean) {
        if (!supported || title.isBlank()) return
        patchNotification(title, artist, playing, end = false)
    }

    fun clear() {
        if (!supported) return
        patchNotification("", "", playing = false, end = true)
    }

    private fun patchNotification(
        title: String,
        artist: String,
        playing: Boolean,
        end: Boolean,
    ) {
        val notificationManager = manager ?: return
        runCatching {
            // getActiveNotifications exposes only this application's own
            // notifications; select audio_service's existing ID explicitly.
            val active = notificationManager.activeNotifications.firstOrNull {
                it.id == MEDIA_NOTIFICATION_ID && it.tag == null
            } ?: return

            val original = active.notification
            if (original.category != Notification.CATEGORY_TRANSPORT &&
                !original.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
            ) {
                return
            }

            if (!end) registerScene(notificationManager)

            val extras = if (end) {
                Bundle().apply {
                    putInt("notification.superx.operation", 2)
                }
            } else {
                val artwork = original.getLargeIcon() ?: Icon.createWithResource(
                    context, context.applicationInfo.icon,
                )
                val color = albumColor(original)
                buildRhythmExtras(title, artist, artwork, color, playing)
            }

            // Recover the SAME Android MediaStyle notification, keeping its
            // media-session token, buttons, foreground flag and seek controls.
            // Updating its exact original ID prevents a second shade card.
            val patched = Notification.Builder.recoverBuilder(context, original)
                .addExtras(extras)
                .build()
            notificationManager.notify(active.tag, active.id, patched)
        }.onFailure {
            Log.w(TAG, "Unable to attach collapsed Island metadata", it)
        }
    }

    private fun registerScene(notificationManager: NotificationManager) {
        if (sceneRegistrationAttempted) return
        sceneRegistrationAttempted = true

        runCatching {
            val method = NotificationManager::class.java.getMethod(
                "setSuperXInfosSceneList",
                MutableList::class.java,
                MutableList::class.java,
                MutableList::class.java,
                MutableList::class.java,
            )
            method.invoke(
                notificationManager,
                arrayListOf(SCENE),
                arrayListOf("true"),
                arrayListOf(context.packageName),
                arrayListOf("true"),
            )
        }.onFailure {
            Log.d(TAG, "Scene registration unavailable; notification extras remain", it)
        }
    }

    private fun buildRhythmExtras(
        title: String,
        artist: String,
        artwork: Icon,
        accent: Int,
        playing: Boolean,
    ): Bundle {
        val base = Bundle().apply {
            putCharSequence("notification.superx.baseInfos.title", title)
            putCharSequence("notification.superx.baseInfos.content", artist)
            putParcelable("notification.superx.baseInfos.icon", artwork)
        }
        val capsule = Bundle().apply {
            putInt("notification.superx.capsule.state", 1)
            putCharSequence("notification.superx.capsule.content", title)
            putParcelable("notification.superx.capsule.icon", artwork)
        }
        val infos = Bundle().apply {
            putString("notification.superx.infos.describe", title)
            putString("notification.superx.infos.coreInfo", artist)
            putParcelable("notification.superx.infos.image", artwork)
        }
        val shortInfos = Bundle().apply {
            putString("notification.superx.shortInfos.describeShort", title)
            putString("notification.superx.shortInfos.coreInfoShort", artist)
            putParcelable("notification.superx.shortInfos.image", artwork)
        }
        val left = Bundle().apply {
            putString("island.superx.leftInfo.content", title)
            putParcelable("island.superx.leftInfo.icon", artwork)
        }
        val right = Bundle().apply {
            putInt("island.superx.rightInfo.waveState", if (playing) 1 else 0)
            putIntegerArrayList(
                "island.superx.rightInfo.waveColor",
                ArrayList(listOf(accent)),
            )
        }
        val island = Bundle().apply {
            putInt("island.superx.leftTemplate", 1)
            // Template 1 = animated rhythm, not progress or expanded card.
            putInt("island.superx.rightTemplate", 1)
            putBundle("island.superx.leftInfo", left)
            putBundle("island.superx.rightInfo", right)
        }

        return Bundle().apply {
            putInt("notification.superx.operation", 0)
            putBoolean("notification.superx.showNotify", true)
            putInt("notification.superx.template", 1)
            putString("notification.superx.scene", SCENE)
            putInt("notification.superx.changedRecord", 0)
            putBundle("notification.superx.baseInfos", base)
            putBundle("notification.superx.capsule", capsule)
            putBundle("notification.superx.infos", infos)
            putBundle("notification.superx.shortInfos", shortInfos)
            putBundle("notification.superx.island", island)
        }
    }

    private fun albumColor(notification: Notification): Int {
        // audio_service already decodes album artwork for MediaStyle. Reuse
        // that bitmap; no network request, palette dependency or new image.
        return runCatching {
            val drawable = notification.getLargeIcon()?.loadDrawable(context)
            val bitmap = (drawable as? BitmapDrawable)?.bitmap
                ?: return@runCatching FALLBACK_COLOR
            if (bitmap.width == 0 || bitmap.height == 0) return@runCatching FALLBACK_COLOR
            pickAccent(bitmap)
        }.getOrDefault(FALLBACK_COLOR)
    }

    private fun pickAccent(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
        val hsv = FloatArray(3)
        var red = 0.0
        var green = 0.0
        var blue = 0.0
        var weightSum = 0.0
        try {
            for (y in 0 until small.height step 2) {
                for (x in 0 until small.width step 2) {
                    val pixel = small.getPixel(x, y)
                    Color.colorToHSV(pixel, hsv)
                    val saturation = hsv[1]
                    val brightness = hsv[2]
                    if (Color.alpha(pixel) < 160 ||
                        saturation < 0.28f || brightness < 0.16f ||
                        brightness > 0.93f
                    ) continue

                    val weight = (saturation * brightness).toDouble()
                    red += Color.red(pixel) * weight
                    green += Color.green(pixel) * weight
                    blue += Color.blue(pixel) * weight
                    weightSum += weight
                }
            }
        } finally {
            if (small !== bitmap) small.recycle()
        }
        if (weightSum <= 0.0) return FALLBACK_COLOR
        return Color.rgb(
            (red / weightSum).toInt().coerceIn(0, 255),
            (green / weightSum).toInt().coerceIn(0, 255),
            (blue / weightSum).toInt().coerceIn(0, 255),
        )
    }
}
