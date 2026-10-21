import 'package:flutter/services.dart';
import 'package:spotube/services/logger/logger.dart';
import 'package:spotube/utils/platform.dart';

/// Adds the Vivo collapsed-rhythm extras to the existing MediaStyle
/// notification; it does not create another notification.
class NativeIsland {
  static const _channel = MethodChannel('spotube/native_origin_island');

  static Future<void> update({
    required String title,
    required String artist,
    required bool playing,
  }) async {
    if (!kIsAndroid || title.isEmpty) return;
    try {
      await _channel.invokeMethod<void>('update', {
        'title': title,
        'artist': artist,
        'playing': playing,
      });
    } on MissingPluginException {
      // Original audio_service notification and seekbar still function.
    } on PlatformException catch (error, stack) {
      AppLogger.reportError(error, stack);
    }
  }

  static Future<void> clear() async {
    if (!kIsAndroid) return;
    try {
      await _channel.invokeMethod<void>('clear');
    } on MissingPluginException {
      // The Android Activity may have been detached.
    } on PlatformException catch (error, stack) {
      AppLogger.reportError(error, stack);
    }
  }
}
