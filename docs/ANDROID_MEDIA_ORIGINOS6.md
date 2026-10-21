# Spotube Android 16 / Vivo OriginOS 6: media and guest Home

## Notification player (single source of truth)

Spotube uses exactly one native Android music notification from
`audio_service` / `MediaSession`. It retains artwork, current song metadata,
play/pause, previous/next, seekbar, and lock-screen/headset controls.

Notification seekbar fixes preserve the active MediaItem even when the
notification service starts asynchronously, keep duration synchronized with
MediaKit, and send explicit seek position updates. We do not post a separate
music progress notification.

## Origin Island: findings and limitations

Spotify-style compact camera-cutout music UI is rendered by Vivo OriginOS:
album cover at left, animated rhythm at right, and an expanded music surface.
The device, not Spotube's Flutter UI, controls the actual animation, sizing,
expansion gestures, and artwork-derived color palette.

Our previous `OriginIslandNotifier` used Android 16 promoted
`Notification.ProgressStyle`, suitable for CardBox live booking updates but
**not** a native Spotify-style music island. It created a duplicate ordinary
notification in the shade. That second notification and its platform bridge
were removed in this change.

Spotube continues supplying song metadata and artwork through Android's
MediaSession; this allows OriginOS native music handling *if the device
supports Spotube*. Some OriginOS versions whitelist which third-party music
players can populate the Island. Public SDKs do not guarantee that a given
third-party app can reproduce Spotify's dynamic music pill.

No notification listener permission, overlay window, package spoofing or
undocumented API is added. Any future independent Island implementation
must be tested on the specific Vivo T4 Ultra firmware and must not add a
second visible notification.

References:
- https://www.vivo.com/en/originos.
- https://github.com/Moriafly/SaltPlayerSource/issues/1941
- https://github.com/cameleonnbss/originos-toolkit/blob/main/docs/ORIGINISLAND.md

## Guest Home: real public albums and playlists

For the **bundled MusicBrainz/ListenBrainz metadata provider only**:

- Signed in: keep original personalized radio, mood, created-for playlists,
  and account-specific fresh releases from the plugin unchanged.
- Guest: use public ListenBrainz `/1/explore/fresh-releases/` for album cards
  and public `/1/playlist/search?query=music` for playlist cards.
- Fetch albums and playlists separately, so slow public playlist searches
  do not prevent new albums from rendering.
- No fake user, credentials, or private API calls are involved.
- For other metadata plugins, keep their own anonymous browsing and release
  support intact instead of suppressing them solely because auth=false.
- Existing search and recently played remain available when offline.
- Public discovery is not a replacement for personal recommendations and
  may be incomplete if ListenBrainz public endpoints are offline or slow.

The two public source APIs are documented in ListenBrainz:
https://listenbrainz.readthedocs.io/en/latest/users/api/misc.html
https://listenbrainz.readthedocs.io/en/latest/users/api/playlist.html

## Verify on Vivo T4 Ultra

1. Confirm exactly **one** music notification in the Android shade.
2. Seek forward/backward on shade media progress; lock and unlock screen.
3. Check wired/Bluetooth earphone single/double/triple presses while locked.
4. Watch whether OriginOS naturally recognizes Spotube's MediaSession at the
   camera cutout; compare Spotify on the same ROM. No custom Island overlay or
   second notification is intended.
5. Start Home as guest; confirm New Releases and Playlists cards load on
   public network. Note that public playlist search may take tens of seconds.
6. Open and play a release and playlist to ensure the bundled metadata
   plugin retrieves the items correctly.
7. Log in to ListenBrainz; verify original personalized home restores.
8. Switch metadata plugins; verify their supported anonymous sections still
   work and audio-source plugin selections remain unchanged.

Static Dart validation and signed APK compilation do not prove Vivo OEM Island
eligibility or public API availability; both require runtime/device testing.
