# Spotube Android 16 / Vivo OriginOS 6 media integration

This experimental branch is for development **without APK builds or PRs**.

## 1. Android media notification seekbar (independent)

Spotube still uses `audio_service` and Android MediaSession for its media
notification, seekbar, notification play/pause/next/previous and lock-screen
controls. The implementation corrects asynchronous MediaItem initialization,
refreshes metadata duration from the player, publishes requested seek
positions, and throttles overly frequent playback-state updates.

MediaSession controls must continue to work even if the Origin Island feature
is unavailable. Network sources must independently support byte-range seek.

## 2. Headset gestures on locked and unlocked screens

AudioService routes media-button events from the headset / Bluetooth
controls to `MobileAudioService.click()`:
- A single generic media-button tap toggles play/pause after a 380 ms window.
- Two quick taps select the next track.
- Three quick taps select the previous track.
- Headphones that emit distinct NEXT / PREVIOUS key codes skip immediately,
  without grouping or delay.
- Queued generic taps are cleared when an explicit Next/Previous event arrives.

Tests of click grouping live in
`test/services/audio_services/headset_click_sequence_test.dart`.

This depends on Android delivering headset media keys to the active Spotube
MediaSession. Earbuds that consume taps in proprietary firmware or assistant
gestures may never emit those key events. Verify while the screen is locked.

## 3. Origin Island: native Vivo status chip, not playback seekbar

The implementation follows the **working CardBox reference**:
`exor-26/CardBox/mobile/android/app/src/main/java/com/starwebx/cardbox/CardboxLiveUpdateManager.kt`.

- Uses Android 16's `Notification.ProgressStyle` and
  `android.requestPromotedOngoing`, not the unverified SuperX-only payload.
- Posts only on Vivo/iQOO devices with API level 36 or higher, and only while
  music is actually playing.
- Shows song title, artist, track progress and a compact remaining-time label.
- Adds media actions routed to the existing `audio_service` MediaButtonReceiver.
- Cancels the separate notification on pause, stop, or cleared track.
- Requires `POST_NOTIFICATIONS` and declares
  `POST_PROMOTED_NOTIFICATIONS` like CardBox.
- Does not post a duplicate ordinary notification when the system reports
  that promotion is not available.
- Keeps the **standard MediaStyle notification and seekbar** independent.

Unlike the CardBox booking/live travel case, a music status may face additional
OriginOS eligibility or music-player whitelist rules; identical Spotify
rendering is **not guaranteed**. The standard MediaSession may already be
integrated by compatible OriginOS builds.

Android reference:
https://developer.android.com/develop/ui/views/notifications/live-update
Music-player whitelist discussion:
https://github.com/Moriafly/SaltPlayerSource/issues/1941

## Test checklist for Vivo T4 Ultra, OriginOS 6 (later — no build yet)

1. Start track while unlocked; grant notifications if prompted.
2. Inspect standard notification: artwork, exact duration and seekbar.
3. Seek to 25%, 50%, and 75% with screen unlocked and locked.
4. Test wired/Bluetooth headset single/double/triple tap in background and
   with screen locked; compare headphones that provide explicit media keys.
5. Check Origin Island while playing, after pausing, on next/previous and
   after stopping; verify no second ordinary shade notification.
6. Tap Island notification to open Spotube, and use previous/pause/next
   actions if shown by the ROM.
7. If the status chip is missing, inspect
   `adb logcat -s SpotubeOriginIsland` for the eligibility results and
   check Vivo's Live Updates / Origin Island app permissions.
8. Inspect `adb shell dumpsys media_session` for seek permission,
   metadata duration, state and headset control events.
9. Compare with CardBox live update on the same OS build.
10. Verify non-Vivo Android receives only the standard MediaSession.

## Validation limits

GitHub Actions validates Dart code and headset-sequence unit tests, without
building an APK. Android Kotlin compilation and actual OriginOS rendering
cannot be certified until the user authorizes an Android build/device test.
