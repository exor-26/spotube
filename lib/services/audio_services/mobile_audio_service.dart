import 'dart:async';
import 'dart:io';

import 'package:audio_service/audio_service.dart';
import 'package:audio_session/audio_session.dart';
import 'package:media_kit/media_kit.dart' hide Track;
import 'package:spotube/provider/audio_player/audio_player.dart';
import 'package:spotube/provider/audio_player/state.dart';
import 'package:spotube/services/audio_player/audio_player.dart';
import 'package:spotube/services/audio_player/playback_state.dart';
import 'package:spotube/services/audio_services/native_island.dart';
import 'package:spotube/services/logger/logger.dart';
import 'package:spotube/utils/platform.dart';

class MobileAudioService extends BaseAudioHandler {
  AudioSession? session;
  final AudioPlayerNotifier audioPlayerNotifier;

  MediaItem? _activeMediaItem;
  DateTime? _lastPlaybackUpdate;
  DateTime? _lastIslandUpdate;

  // ignore: invalid_use_of_protected_member, invalid_use_of_visible_for_testing_member
  AudioPlayerState get playlist => audioPlayerNotifier.state;

  MobileAudioService(this.audioPlayerNotifier) {
    AudioSession.instance.then((s) {
      session = s;
      session?.configure(const AudioSessionConfiguration.music());

      bool wasPausedByBeginEvent = false;

      s.interruptionEventStream.listen((event) async {
        if (event.begin) {
          switch (event.type) {
            case AudioInterruptionType.duck:
              await audioPlayer.setVolume(0.5);
              break;
            case AudioInterruptionType.pause:
            case AudioInterruptionType.unknown:
              wasPausedByBeginEvent = audioPlayer.isPlaying;
              await audioPlayer.pause();
              break;
          }
        } else {
          switch (event.type) {
            case AudioInterruptionType.duck:
              await audioPlayer.setVolume(1.0);
              break;
            case AudioInterruptionType.pause when wasPausedByBeginEvent:
            case AudioInterruptionType.unknown when wasPausedByBeginEvent:
              await audioPlayer.resume();
              wasPausedByBeginEvent = false;
              break;
            default:
              break;
          }
        }
      });

      s.becomingNoisyEventStream.listen((_) {
        audioPlayer.pause();
      });
    });

    audioPlayer.playerStateStream.listen((state) async {
      if (state == AudioPlaybackState.playing) {
        await session?.setActive(true);
      }
      _publishPlaybackState(force: true);
    });

    // The native MediaSession can extrapolate position between state updates.
    // Flooding SystemUI with per-frame updates makes scrubbing unstable.
    audioPlayer.positionStream.listen((_) => _publishPlaybackState());
    audioPlayer.bufferedPositionStream.listen((_) => _publishPlaybackState());

    // Keep MediaSession duration current: Android disables or misdraws the
    // seek thumb when the current MediaItem has no accurate duration.
    audioPlayer.durationStream.listen((duration) {
      final current = _activeMediaItem;
      if (current == null || duration <= Duration.zero) return;

      final previous = current.duration;
      if (previous == null ||
          (previous - duration).abs() > const Duration(seconds: 1)) {
        _activeMediaItem = current.copyWith(duration: duration);
        mediaItem.add(_activeMediaItem);
        _publishPlaybackState(force: true);
      }
    });

    _publishPlaybackState(force: true);
  }

  void addItem(MediaItem item) {
    _activeMediaItem = item;
    mediaItem.add(item);
    session?.setActive(true);
    _publishPlaybackState(force: true);
  }

  @override
  Future<void> play() => audioPlayer.resume();

  @override
  Future<void> pause() => audioPlayer.pause();

  @override
  Future<void> seek(Duration position) async {
    final duration = _activeMediaItem?.duration;
    final target = position < Duration.zero
        ? Duration.zero
        : duration != null && duration > Duration.zero && position > duration
            ? duration
            : position;
    await audioPlayer.seek(target);
    // Show the new scrub location as soon as the native request is handled.
    _publishPlaybackState(force: true, positionOverride: target);
  }

  @override
  Future<void> setShuffleMode(AudioServiceShuffleMode shuffleMode) async {
    await super.setShuffleMode(shuffleMode);
    await audioPlayer.setShuffle(shuffleMode == AudioServiceShuffleMode.all);
    _publishPlaybackState(force: true);
  }

  @override
  Future<void> setRepeatMode(AudioServiceRepeatMode repeatMode) async {
    await super.setRepeatMode(repeatMode);
    await audioPlayer.setLoopMode(switch (repeatMode) {
      AudioServiceRepeatMode.all ||
      AudioServiceRepeatMode.group =>
        PlaylistMode.loop,
      AudioServiceRepeatMode.one => PlaylistMode.single,
      _ => PlaylistMode.none,
    });
    _publishPlaybackState(force: true);
  }

  @override
  Future<void> stop() async {
    await audioPlayerNotifier.stop();
    _activeMediaItem = null;
    mediaItem.add(null);
    _publishPlaybackState(force: true);
    unawaited(NativeIsland.clear());
  }

  @override
  Future<void> skipToNext() async {
    await audioPlayer.skipToNext();
    await super.skipToNext();
  }

  @override
  Future<void> skipToPrevious() async {
    await audioPlayer.skipToPrevious();
    await super.skipToPrevious();
  }

  @override
  Future<void> onTaskRemoved() async {
    unawaited(NativeIsland.clear());
    await audioPlayer.pause();
    if (kIsAndroid) exit(0);
  }

  void _publishPlaybackState({
    bool force = false,
    Duration? positionOverride,
  }) {
    final now = DateTime.now();
    if (!force &&
        _lastPlaybackUpdate != null &&
        now.difference(_lastPlaybackUpdate!) < const Duration(seconds: 1)) {
      _publishIsland();
      return;
    }

    _lastPlaybackUpdate = now;
    try {
      playbackState.add(_transformEvent(positionOverride: positionOverride));
      _publishIsland(force: force);
    } catch (error, stack) {
      AppLogger.reportError(error, stack);
    }
  }

  void _publishIsland({bool force = false}) {
    if (!kIsAndroid) return;
    final item = _activeMediaItem;
    if (item == null || !audioPlayer.hasSource) {
      return;
    }

    final now = DateTime.now();
    if (!force &&
        _lastIslandUpdate != null &&
        now.difference(_lastIslandUpdate!) < const Duration(seconds: 4)) {
      return;
    }
    _lastIslandUpdate = now;

    // This updates extras on the *existing* audio_service notification.
    // No extra notification ID, ongoing card or overlay is created.
    unawaited(
      NativeIsland.update(
        title: item.title,
        artist: item.artist ?? '',
        playing: audioPlayer.isPlaying,
      ),
    );
  }

  PlaybackState _transformEvent({Duration? positionOverride}) {
    return PlaybackState(
      controls: [
        MediaControl.skipToPrevious,
        audioPlayer.isPlaying ? MediaControl.pause : MediaControl.play,
        MediaControl.skipToNext,
        MediaControl.stop,
      ],
      systemActions: const {MediaAction.seek},
      androidCompactActionIndices: const [0, 1, 2],
      playing: audioPlayer.isPlaying,
      updatePosition: positionOverride ?? audioPlayer.position,
      bufferedPosition: audioPlayer.bufferedPosition,
      shuffleMode: audioPlayer.isShuffled
          ? AudioServiceShuffleMode.all
          : AudioServiceShuffleMode.none,
      repeatMode: switch (audioPlayer.loopMode) {
        PlaylistMode.loop => AudioServiceRepeatMode.all,
        PlaylistMode.single => AudioServiceRepeatMode.one,
        _ => AudioServiceRepeatMode.none,
      },
      processingState: !audioPlayer.hasSource
          ? AudioProcessingState.idle
          : audioPlayer.isBuffering
              ? AudioProcessingState.buffering
              : AudioProcessingState.ready,
    );
  }
}
