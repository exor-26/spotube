# Original Spotube baseline — Android 16 / Vivo OriginOS 6

Baseline: upstream `team-spotube/spotube` / fork `master` at
`69a310c78f5ceaf4eab7dfee98f187d38211c9ba`.

This feature branch changes **only** Android media notification seeking and
the native collapsed Origin Island. It does not contain prior custom onboarding,
guest Home, headset, or dual-notification experiments.

## 1. Original notification — seekbar

The current MediaSession shares one notification built by the upstream
`audio_service` Flutter plugin. Its `MediaItem` is kept synchronized with
player duration, track identity and artwork, while the seek action and requested
scrub position are published immediately. Playback-state updates are throttled
to prevent unnecessary native seeks and progress glitches. Buffering is now
represented as `buffering`, not the misleading initial `loading` state.

Android's **system UI**, not Flutter, draws the progress thumb/dot. Spotube
supplies accurate metadata and ACTION_SEEK_TO to allow an active seekbar; the
device UI decides the visual thumb geometry. We cannot force a circular dot
from an Android MediaSession API.

## 2. Actual Origin Island — collapsed camera pill

The native code targets the Vivo/iQOO Android 16 rhythm template:

- **Left**: actual MediaStyle album-cover icon, not a separate app icon.
- **Right**: template 1 animated rhythm; `waveState = 1` while playing,
  `waveState = 0` while paused.
- **Wave color**: sampled from saturated pixels of the current album art.
- **Expand/tap**: Vivo's system draws this when it recognizes the Origin
  Island extras and handles the system interaction.
- **No second notification**: `NotificationManager.getActiveNotifications()`
  locates the current upstream `audio_service` MediaStyle notification
  (ID 1124). The Android native bridge uses
  `Notification.Builder.recoverBuilder` and posts the updated object back
  to the same ID. The MediaStyle notification, MediaSession token,
  foreground-service state, controls, seekbar and headset handling remain.

The extra keys are based on reverse engineering documented at
https://github.com/cameleonnbss/originos-toolkit/blob/main/docs/ORIGINISLAND.md
and the framework protocol is not part of the public Android SDK.

This branch makes no package identity substitutions, seeks no notification
listener access and creates no floating overlay. It only alters Spotube's
own currently-active music notification. Native Kotlin compilation is checked
in GitHub Actions; final behavior on the particular ROM requires a test.

## Verify on Vivo T4 Ultra, OriginOS 6

1. Confirm exactly one music notification in the shade.
2. Check that the seekbar can be dragged on unlocked and lock screen, has
   correct time/duration, and shows the desired circular drag thumb.
3. Observe the **collapsed** pill while playing with screen unlocked and
   another app foregrounded: album cover left, rhythmic bars right.
4. Change to an album with very different artwork colors and check the
   rhythm color changes.
5. Pause/resume, switch songs and tap the pill to expand; check lifecycle.
6. Review logcat `SpotubeOriginIsland` if the native payload is ignored.
7. Compare Spotify on the same OriginOS build, without confusing Spotify's
   expanded music controls with the collapsed pill.
8. Check that the regular Android music notification still seeks/plays,
   even if the OriginOS integration does not render.

### Compatibility detail

The existing audio_service notification ID is a pinned implementation
detail of audio_service 0.18.x. If this dependency changes, re-check ID 1124.
This code intentionally fails quietly when no active media notification is
available rather than creating a fallback music notification.
