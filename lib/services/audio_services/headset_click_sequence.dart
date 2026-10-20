import 'dart:async';

/// Groups consecutive headset media-button presses into one logical action.
///
/// The native Android MediaSession routes hardware media keys to
/// AudioHandler.click. A short delay is needed to tell a single tap (toggle)
/// from two taps (next track) or three taps (previous track).
///
/// Explicit NEXT/PREVIOUS hardware keys must not be delayed or grouped.
class HeadsetClickSequence {
  HeadsetClickSequence({
    required this.onSequence,
    this.maxGap = const Duration(milliseconds: 380),
  });

  final Future<void> Function(int pressCount) onSequence;
  final Duration maxGap;

  Timer? _timer;
  int _count = 0;

  void register() {
    _count++;
    _timer?.cancel();
    _timer = Timer(maxGap, () {
      final count = _count;
      _count = 0;
      _timer = null;
      if (count > 0) unawaited(onSequence(count));
    });
  }

  void cancelPending() {
    _timer?.cancel();
    _timer = null;
    _count = 0;
  }

  void dispose() => cancelPending();
}
