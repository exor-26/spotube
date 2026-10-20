import 'package:flutter/services.dart';
import 'package:spotube/services/logger/logger.dart';
import 'package:spotube/utils/platform.dart';

/// Best-effort OriginOS 6 native island notification bridge.
/// Android's regular MediaSession remains the playback source of truth.
class OriginIslandBridge {
  static const _channel = MethodChannel('spotube/origin_island');

  static Future<void> update({
    required String title,
    required String artist,
    required Duration position,
    required Duration duration,
    required bool playing,
  }) async {
    if (!kIsAndroid || title.isEmpty) return;
    try {
      await _channel.invokeMethod<void>('update', {
        'title': title,
        'artist': artist,
        'positionMs': position.inMilliseconds,
        'durationMs': duration.inMilliseconds,
        'playing': playing,
      });
    } on MissingPluginException {
      // Non-standard Android embedding: the media session still works.
    } on PlatformException catch (error, stack) {
      AppLogger.reportError(error, stack);
    }
  }

  static Future<void> clear() async {
    if (!kIsAndroid) return;
    try {
      await _channel.invokeMethod<void>('clear');
    } on MissingPluginException {
      // Nothing to clear if the native bridge is absent.
    } on PlatformException catch (error, stack) {
      AppLogger.reportError(error, stack);
    }
  }
}
