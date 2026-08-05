# Drift — Android TV Sleep Sounds

A D-pad-first Android TV app that loops ambient sleep sounds behind full-screen
artwork, mixes up to three layers, fades out on a sleep timer, and offers a
"lights out" mode that blacks the screen while audio keeps playing.

## Visual design

The look was restyled from a wellness-app reference (dark violet chrome, a
glowing mood-circle, rounded warm-glow photo cards, capsule day-picker). Drift
keeps the reference's *structure* but shifts the palette cool/nocturnal, since
it's a sleep app rather than a morning-wellness one:

| Token | Value | Used for |
|---|---|---|
| Void | `#150F24` | app background |
| Panel | `#211A38` | cards, surfaces |
| Accent Violet | `#8B5CF6` | focus, primary buttons, timer ring |
| Accent Magenta | `#D946EF` | gradient partner (focus borders, timer ring) |
| Moon White | `#F5F3FF` | glow halo, focused-chip border |
| Moonlight / Moon Dim | `#EDEBF7` / `#9B93B8` | primary / secondary text |

Structural borrows, adapted for a 10-foot D-pad UI:
- **Glowing mood-circle → Moon button.** The reference's smiley-with-halo
  becomes Drift's "lights out" control: a crescent moon inside a soft radial
  glow (`PlayerScreen.kt` → `MoonButton`).
- **"70% today" progress ring → sleep-timer ring.** Same circular motif, now
  functionally tied to time remaining rather than a static number
  (`PlayerScreen.kt` → `TimerRing`).
- **Day-picker capsules → category filter.** The home grid's Nature/Noise/
  Ambient chips reuse the same pill shape and selected-state fill
  (`HomeScreen.kt` → `CategoryChip`).
- **Warm-lit photo cards → cool glow-vignette art.** Tile artwork keeps the
  reference's radial-glow-behind-subject technique, recolored per sound
  (violet-teal for ocean, icy lavender for white noise, ember for brown noise)
  instead of daylight gold, since glare and warm tones fight against
  fall-asleep use.

## Building

Open the project in Android Studio (Ladybug+) or run:

    ./gradlew :app:assembleDebug      # or :app:assembleRelease — both are
                                       # auto-signed with the debug keystore
                                       # and installable as-is, no store setup

Install with `adb install app/build/outputs/apk/debug/app-debug.apk` (or the
`release/app-release.apk` path for the release variant). minSdk 26, target 35.
Note: no Gradle wrapper JAR is committed — run `gradle wrapper --gradle-version 8.10`
once, or open in Android Studio which handles it.

This project is set up for direct APK installs only (no Play Store submission).
See **Sideloading** below for the two settings you need to flip on the TV
itself before `adb install` will work.

## Architecture

| Layer | Choice | Why |
|---|---|---|
| UI | Jetpack Compose + `androidx.tv:tv-material` | Compose-for-TV components (focus-aware `Surface`, TV Material theme) with plain Compose focus APIs — no Leanback fragments. |
| Playback | Media3 `ExoPlayer`, one player per layer, `REPEAT_MODE_ONE` | Media3 loops a single item gaplessly, which is what makes the loops seamless. Multiple full players (max 3) is the simplest reliable mixer with independent volume. |
| Service | `MediaSessionService` (`playback/PlaybackService.kt`) | Foreground media service: playback survives UI teardown, integrates hardware play/pause keys, and shows the required media notification. The UI binds via a `LocalBinder` for mixer/timer control beyond what `MediaSession` models. |
| Sleep timer | Coroutine countdown inside the service | Lives in the service so it runs while the Activity is dimmed/paused. Volume fades over the final 90 s, then playback stops and the service can be dismissed. |
| State | `StateFlow` from service → `DriftViewModel` → Compose | Single source of truth in the service. |
| Persistence | DataStore (`data/Prefs.kt`) | Per-sound volume memory ("adaptive volume"). |
| Assets | Bundled in APK + `assets/manifest.json` | Offline-first; adding a sound means editing the manifest and rebuilding — no update pipeline needed since there's no store release cycle to work around. |

## Adding sounds

Drop the files into `app/src/main/assets/sounds/` and `assets/images/`, then add
an entry to `app/src/main/assets/manifest.json`:

```json
{
  "id": "rain",              // unique, stable — used as the prefs key
  "title": "Rain",
  "category": "Nature",       // free-form; used for labeling (rows later)
  "audio": "sounds/rain.ogg", // OGG/Vorbis or AAC, mono is fine
  "image": "images/rain.jpg", // 1280x720+ artwork
  "defaultVolume": 0.8
}
```

**Making a loop seamless:** crossfade the file's tail into its head (1–2 s) in an
editor, or with ffmpeg/sox, so sample 0 and the last sample are continuous. The
bundled samples were generated exactly this way (see below). Licensing: any real
recordings you add must be cleared for redistribution — the bundled sounds are
synthesized, so they carry no third-party rights.

## "Screen off" — what it really is, and why

Android TV apps **cannot** turn off or dim the panel: there is no app-side
`SCREEN_BRIGHTNESS` on TV, and CEC standby is (a) privileged and (b) cuts audio
on most TVs. So Drift's lights-out mode is:

1. A full-screen `#000000` Compose layer over an immersive (no system bars)
   window with a black window background — on OLED those pixels are literally
   off; on LCD the backlight stays on but the room reads dark.
2. `FLAG_KEEP_SCREEN_ON` while dimmed, so the system screensaver/ambient mode
   doesn't replace our black view with something bright.
3. **Wake-to-check:** while dimmed, any remote key is swallowed and instead
   fades in a translucent overlay (current mix + timer remaining) for ~4 s,
   then fades back to black. BACK exits lights-out.

If you want to experiment with real panel standby, `HdmiControlManager`
(`android.permission.HDMI_CEC` — signature/privileged) can send `<Standby>`, but
expect audio to drop on most TVs; ship it only as an off-by-default,
clearly-labeled experimental setting, and only if you control the device image.

Device-specific caveats: OEMs differ on whether long-idle foreground apps get
frozen; the foreground media service + media notification is what keeps
playback alive for hours. Test on at least one Chromecast with Google TV or
Shield — emulator focus/remote behavior is not representative.

## Sideloading

No Play Store submission, so most of the usual TV certification checklist
(content rating questionnaire, Play Console screenshots, `uses-feature`
device-filtering hints) doesn't apply and has been left out of the manifest.
Two things still matter because they're read by the TV itself, not by any
store:

- **`LEANBACK_LAUNCHER` intent filter + `android:banner`** — this is what
  makes Drift show up as a tile on the Android TV / Google TV home screen
  instead of only being launchable via `adb shell am start`.
- **On the TV, before installing:** Settings → enable Developer options (About
  → click Build repeatedly), then turn on **USB debugging** and **Install
  unknown apps** (or **Apps from unknown sources**, wording varies by OEM) for
  whichever app you're installing through (Files, a browser, or ADB).

Then: `adb connect <tv-ip>:5555` (if not on USB) and
`adb install app/build/outputs/apk/debug/app-debug.apk`. Reinstalling over an
existing install works fine — no versionCode discipline needed since there's
no store to reject an out-of-order update.

## Bundled sample sounds

`ocean_waves`, `white_noise`, `pink_noise`, `brown_noise` — all synthesized
(filtered/integrated noise with amplitude-modulated swells for the ocean),
30 s loops, tail-to-head crossfaded for seamlessness, OGG/Vorbis q4. Free of
any third-party licensing.

## Roadmap hooks already in the code

- `category` field supports switching the grid to Leanback-style category rows.
- `Prefs` is the natural home for favorites/recents and saved mixes.
- Ken Burns on artwork: wrap `AssetImage` in an infinite `animateFloat` scale.
