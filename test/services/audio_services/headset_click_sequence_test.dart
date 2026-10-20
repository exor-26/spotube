import 'package:flutter_test/flutter_test.dart';
import 'package:spotube/services/audio_services/headset_click_sequence.dart';

void main() {
  const tapGap = Duration(milliseconds: 20);
  const settle = Duration(milliseconds: 120);

  test('one headset tap toggles playback once', () async {
    final presses = <int>[];
    final clicks = HeadsetClickSequence(
      maxGap: const Duration(milliseconds: 70),
      onSequence: (count) async => presses.add(count),
    );
    clicks.register();
    await Future<void>.delayed(settle);
    expect(presses, [1]);
    clicks.dispose();
  });

  test('two quick taps produce one Next action', () async {
    final presses = <int>[];
    final clicks = HeadsetClickSequence(
      maxGap: const Duration(milliseconds: 70),
      onSequence: (count) async => presses.add(count),
    );
    clicks.register();
    await Future<void>.delayed(tapGap);
    clicks.register();
    await Future<void>.delayed(settle);
    expect(presses, [2]);
    clicks.dispose();
  });

  test('three quick taps produce one Previous action', () async {
    final presses = <int>[];
    final clicks = HeadsetClickSequence(
      maxGap: const Duration(milliseconds: 70),
      onSequence: (count) async => presses.add(count),
    );
    clicks.register();
    await Future<void>.delayed(tapGap);
    clicks.register();
    await Future<void>.delayed(tapGap);
    clicks.register();
    await Future<void>.delayed(settle);
    expect(presses, [3]);
    clicks.dispose();
  });

  test('cancelling a pending tap prevents delayed playback action', () async {
    final presses = <int>[];
    final clicks = HeadsetClickSequence(
      maxGap: const Duration(milliseconds: 70),
      onSequence: (count) async => presses.add(count),
    );
    clicks.register();
    clicks.cancelPending();
    await Future<void>.delayed(settle);
    expect(presses, isEmpty);
    clicks.dispose();
  });

  test('separated taps are two distinct single presses', () async {
    final presses = <int>[];
    final clicks = HeadsetClickSequence(
      maxGap: const Duration(milliseconds: 70),
      onSequence: (count) async => presses.add(count),
    );
    clicks.register();
    await Future<void>.delayed(settle);
    clicks.register();
    await Future<void>.delayed(settle);
    expect(presses, [1, 1]);
    clicks.dispose();
  });
}
