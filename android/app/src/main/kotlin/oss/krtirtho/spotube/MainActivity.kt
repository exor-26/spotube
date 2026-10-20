package oss.krtirtho.spotube

import android.content.pm.PackageManager
import com.ryanheise.audioservice.AudioServiceActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

// AudioServiceActivity keeps Flutter's MediaBrowser / notification integration.
class MainActivity : AudioServiceActivity() {
    private val island by lazy { OriginIslandNotifier(this) }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "spotube/origin_island")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "update" -> {
                        island.update(
                            title = call.argument<String>("title").orEmpty(),
                            artist = call.argument<String>("artist").orEmpty(),
                            positionMs = call.argument<Number>("positionMs")?.toLong() ?: 0L,
                            durationMs = call.argument<Number>("durationMs")?.toLong() ?: 0L,
                            playing = call.argument<Boolean>("playing") ?: false,
                        )
                        result.success(null)
                    }
                    "clear" -> {
                        island.clear()
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == OriginIslandNotifier.PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            island.onNotificationPermissionGranted()
        }
    }
}
