package oss.krtirtho.spotube

import com.ryanheise.audioservice.AudioServiceActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * The existing MediaSession continues to own the single music notification.
 * The native bridge adds OriginOS metadata to that same notification only.
 */
class MainActivity : AudioServiceActivity() {
    private val island by lazy { NativeOriginIsland(applicationContext) }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "spotube/native_origin_island",
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "update" -> {
                    island.update(
                        title = call.argument<String>("title").orEmpty(),
                        artist = call.argument<String>("artist").orEmpty(),
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
}
