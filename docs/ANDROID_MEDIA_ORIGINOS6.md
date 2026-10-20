# Android media controls and OriginOS 6 — development notes

Work-in-progress branch: `fix/android-media-origin-island`. No APK or release build is part of this branch's workflow.

## Notification seekbar

The media notification continues to use the existing `audio_service` Android
MediaSession. These changes address the client-side contract:

- Initialize the service asynchronously **without dropping** a playlist/metadata update.
- Read active media from the actual MediaKit playlist event, not a potentially stale
  Riverpod snapshot.
- Publish MediaItem with a duration, then refresh it when the decoder reports
  a more accurate duration.
- Keep `MediaAction.seek` enabled and pass requested seek positions directly to
  `audioPlayer.seek`; publish the resulting position immediately.
- Distinguish buffering from initial loading and avoid flooding SystemUI with
  per-frame playback-state updates.

This does not alter how JioSaavn or any other audio-source plugin resolves/streams
the actual media. The remote server must still support seeking/range requests.

## OriginOS 6 (Vivo / iQOO Android 16)

The native `OriginIslandNotifier` posts a **real Android notification** (not
a window overlay) with the reverse-engineered Vivo `notification.superx.*`
extras, title/artist, progress, and the app icon.

- Only activated for Vivo/iQOO builds with API level 36 or later.
- Permission: `POST_NOTIFICATIONS`, requested once when needed for this separate
  notification; denial leaves ordinary audio playback/MediaSession intact.
- Standard media notification remains the source of truth for play/pause/seek.
- Updates roughly every 5 seconds while playing; removes the island notification
  when media is cleared / playback stops.
- Uses a low-importance, silent, separate notification channel.
- No changes to package identity, audio-source plugins, or system settings.

**Experimental status:** The SuperX protocol is **not a public Android or Vivo
SDK**. ROM versions can ignore the extras or whitelist supported apps. We
cannot assert that the Vivo T4 Ultra on OriginOS 6 will render the pill until
it is tested on that actual device. It must not be described as verified support.

Protocol documentation:
https://github.com/cameleonnbss/originos-toolkit/blob/main/docs/ORIGINISLAND.md

## Later real-device checks (do not build yet)

1. On Vivo T4 Ultra, verify Android 16 and OriginOS 6 build/version and
   enable Origin Player / system media controls.
2. Grant Spotube notification permission if requested. Check that both the
   stock media control and optional Origin Island appear as intended.
3. Select a JioSaavn audio source and play a known 3–5 minute track.
4. Confirm notification metadata includes accurate duration and progress.
5. Drag shade seekbar to 25%, 50%, and 75%; confirm actual audio jumps, both
   while the app is foreground and background.
6. Pause, resume, skip, seek backward, change track and clear queue. Check the
   Origin Island track information and lifecycle each time.
7. Restart Spotube and compare behavior with a locally stored track.
8. Verify non-Vivo Android uses standard media notification only.
9. Inspect `adb shell dumpsys media_session` for Spotube: metadata duration,
   active session, position and `ACTION_SEEK_TO`.
10. Inspect `adb shell dumpsys notification` and `adb logcat` for the Vivo
    status notification if the island does not show. Confirm the system's
    notification permissions and ROM app-eligibility policies.

## Validation limits

Dart analyzer and Dart formatter can be run by GitHub Actions without
compiling an APK. Kotlin Android code and Vivo's closed-source rendering
require a later Android build and device test; neither should be marked
passed from static analysis alone.
