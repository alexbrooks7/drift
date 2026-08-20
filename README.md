# Drift — Android TV Sleep Sounds

A D-pad-first Android TV app that loops ambient sleep sounds behind full-screen
artwork, mixes up to three layers, fades out on a sleep timer, and offers a
"lights out" mode that blacks the screen while audio keeps playing.

**[Privacy Policy](docs/privacy-policy.md)** — what leaves the device, what
stays on it, and how the two build flavors differ.

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

    ./gradlew :app:assembleSideloadDebug     # the full app, sharing SDK included
    ./gradlew :app:assembleStoreDebug        # store-safe: no sharing SDK at all

Both are auto-signed with the debug keystore and installable as-is, no store
setup. Release variants (`assembleSideloadRelease` / `assembleStoreRelease`)
are signed the same way — fine for sideloading, but see **Build flavors**
below before submitting to a store.

Output paths follow the flavor, e.g.
`app/build/outputs/apk/sideload/debug/app-sideload-debug.apk`.
minSdk 26, target 35. The Gradle wrapper is committed (pinned to 9.4.1), so
`./gradlew` works on a fresh clone with no local Gradle install.

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
| Persistence | DataStore (`data/Prefs.kt`) | Per-sound volume memory, plus the last mix and sleep-timer length — this is a nightly app, so the home screen offers "Resume last mix" rather than making you rebuild it. |
| Assets | Bundled in APK + `assets/manifest.json` | All playback is offline; adding a sound means editing the manifest and rebuilding — no update pipeline needed since there's no store release cycle to work around. |
| Analytics | PostHog (`analytics/DriftAnalytics.kt`) | The one networked part of the app — see **Analytics** below. |

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

Lights-out is also entered **automatically**: the controls fade after 12 s idle
and the screen blacks out 60 s after that. Pressing the moon is the manual
shortcut, not the only route — the expected flow is to pick a sound, put the
remote down, and let the room go dark on its own.

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

## Analytics

Drift uses [PostHog](https://posthog.com) for basic usage analytics — DAU/WAU/MAU,
retention, and which sounds/features actually get used. It was chosen over
Firebase Analytics specifically because it doesn't depend on Google Play
Services, so it works on any Android TV device including Fire TV, not just
Google TV / Android TV boxes.

**Setup:** create a free account at posthog.com, then add your project API
key to `local.properties` (gitignored, same file as `sdk.dir`):

```properties
posthog.apiKey=phc_your_key_here
posthog.host=https://us.i.posthog.com   # eu.i.posthog.com for EU-region accounts
```

Leave `posthog.apiKey` blank and the app builds and runs exactly as before —
`DriftAnalytics.init()` checks for a blank key and no-ops instead of calling
the SDK, so a fresh clone doesn't require a PostHog account just to build.

**What's tracked**, all from `analytics/DriftAnalytics.kt`:

| Event | Fires when | Key properties |
|---|---|---|
| *(automatic)* | App opened/backgrounded | — gives DAU/MAU for free, no code needed |
| `sound_played` | A tile is picked from Home | `sound_id`, `category` |
| `layer_added` / `layer_removed` | Mix another sound / long-press remove | `sound_id`, `layer_count` |
| `mix_resumed` | "Resume last mix" tapped | `sound_ids`, `layer_count` |
| `sleep_timer_set` / `_cancelled` | Timer picker | `minutes` |
| `lights_out_triggered` | Screen dims | `trigger`: `manual` (moon button) vs `auto_idle` |
| *(screen)* `home` / `player` | Navigating between the two screens | — manual stand-in for screen tracking; see the class doc for why |

**What's deliberately not tracked:** session replay is explicitly disabled
(`sessionReplay = false`) — recording a screen that's designed to go black
for hours is both useless and needlessly invasive even if PostHog supports
opt-in replay elsewhere. No PII is collected; PostHog's default anonymous
device ID is all that identifies a "user."

## Download

[**Download the latest APK**](https://github.com/alexbrooks7/drift/releases/latest/download/Drift.apk)
— that link always serves the newest release, so it doesn't change between
versions.

To sideload on Android TV / Fire TV: enable installs from unknown sources in
the device's settings, then either open the downloaded file with a file
manager, or `adb install -r Drift.apk`. On Fire TV the Downloader app takes
the URL above directly.

## Build flavors

Two distribution channels with different legal constraints, so the project
builds two variants:

| Flavor | Channel | Sharing SDK | applicationId | Release APK |
|---|---|---|---|---|
| `sideload` | GitHub releases, direct APK | included | `com.drift.tv` | ~85 MB |
| `store` | Google Play, Amazon Appstore | **absent** | `com.drift.tv.store` | ~17 MB |

```bash
./gradlew assembleSideloadRelease   # app/build/outputs/apk/sideload/release/
./gradlew assembleStoreRelease      # app/build/outputs/apk/store/release/
```

Google Play's Device and Network Abuse policy and the Amazon Appstore's
equivalent both prohibit SDKs that route third-party traffic through a user's
connection. **Leaving `pawns.apiKey` blank is not sufficient for those
stores** — that only disables the SDK at runtime, and
`libpawns_mobile_sdk.so` plus the rest of the native relay libraries would
still be sitting in the APK for a reviewer to find.

So the dependency is declared `sideloadImplementation`. For the store flavor
the SDK is not on the classpath at all: no native libraries, no
`FOREGROUND_SERVICE_SPECIAL_USE` permission, no peer service in the merged
manifest, and zero `com/pawns` references in the DEX. Verified by unzipping
the built APK, not just assumed.

The two flavors share all UI. `PawnsManager` has a per-flavor implementation
(`src/sideload/` real, `src/store/` a no-op with the same API), and shared
code talks to a Drift-owned `SharingStatus` type rather than the SDK's own
`ServiceState`, so nothing in `src/main/` references the SDK. In store builds
`PawnsManager.available` is false, which already hides the settings entry and
suppresses the consent prompt — the sharing feature simply doesn't exist there.

The differing `applicationId` lets both be installed side by side for testing,
and keeps a store listing from colliding with the directly-distributed APK.

## Internet sharing

Drift can optionally integrate [Pawns.app](https://pawns.app)'s bandwidth-sharing
SDK: with explicit consent, a portion of the device's internet connection is
used to relay traffic for Pawns' clients, and the app owner earns revenue for
it. This is a fundamentally different kind of feature than anything else in
this app — it has nothing to do with playing sounds, and it means someone
else's internet traffic routes through the installing device. It's built
behind real, informed, revocable consent, not a quiet default-on toggle. If
that's not something you want in your build, just don't set `pawns.apiKey`
below and none of this code path ever activates.

**Setup:** getting an API key requires contacting a Pawns.app representative
directly — there's no self-serve signup like PostHog. Once you have one:

```properties
pawns.apiKey=your_key_here
```

Leave it blank (the default) and the feature doesn't exist from the user's
perspective: no settings entry on Home, no consent screen, `PawnsManager`
never touches the SDK. See `sharing/PawnsManager.kt`.

**Design choices worth knowing about:**

- **Consent is asked once, on app open** (`ui/ConsentScreen.kt`), as a
  one-screen dialog over Home. Either answer is recorded in `Prefs` and the
  prompt never returns — you can still opt in later from the sharing screen's
  START button.
- **Why not the SDK's bundled consent Activity.** It was tested on a TV first
  (`getConsentIntent()` still exists in `PawnsManager` if you want to compare).
  It *is* D-pad navigable, but every hyperlink paragraph is its own focus
  stop, so reaching the buttons took ~13 presses; its buttons render no focus
  indicator at all, so you can't see what's selected; and it's a full-white
  scrolling page. The dialog has exactly two focus stops and an unmistakable
  focus ring. Custom consent UIs are explicitly permitted by the SDK.
- **Accuracy of the disclosure copy.** The dialog states that Pawns.app
  receives IP and approximate location, that it uses data/battery and can
  affect internet speed, and that metered or restricted connections should
  skip it. That's what Pawns' own consent document says. Copy claiming "no
  personal data is collected" or "does not affect performance" would
  contradict the vendor's own disclosures, and Pawns puts responsibility for
  how the feature is presented on the app owner — so an inaccurate prompt is
  the app owner's liability, not theirs.
- **Foreground service, not background.** The SDK offers both; Drift always
  uses `ServiceType.FOREGROUND`, which means a permanent, high-priority
  notification stays visible the entire time sharing is active. That's
  deliberate — someone should always be able to see, at a glance, that
  sharing is on, and find their way to turn it off.
- **Turning off is always one press,** from Settings' "Turn off" — no
  re-confirmation. "Review what this shares" reopens the same disclosure, and
  declining there withdraws consent *and* stops an active session.
- **Status comes from the SDK's service-state flow,** not the consent flag.
  Consent being granted doesn't prove the service is running, so Settings
  reports what's actually happening — including the failure reason when the
  SDK can't start.

**APK size:** this adds roughly 65 MB (the SDK's native relay engine, ×4 CPU
architectures) regardless of whether a key is configured — Gradle can't
strip an unused native dependency from the APK just because a runtime flag
is blank. If that's not an acceptable trade for a build that never uses the
feature, the dependency in `app/build.gradle.kts` and `settings.gradle.kts`'
JitPack repository entry can simply be removed.

## Bundled sounds

Ten sounds, all synthesized — no recordings, so nothing here carries
third-party rights:

| Category | Sounds |
|---|---|
| Nature | `ocean_waves` (90 s), `rain`, `stream`, `wind`, `thunder` (60 s) |
| Ambient | `fireplace`, `fan` |
| Noise | `white_noise`, `pink_noise`, `brown_noise` |

They're generated by **`tools/GenerateAssets.java`**, which also renders the
tile artwork. It's a single dependency-free file with fixed seeds, so
re-running it reproduces the same output:

    java tools/GenerateAssets.java                    # -> app/src/main/assets
    ffmpeg -i x.wav -c:a libvorbis -q:a 4 x.ogg       # WAV is the intermediate

Loop lengths are chosen per texture: stationary sounds (fan, noise) can be
short because the ear can't find a seam in them, while anything with
recognisable events needs longer — `ocean_waves` is 90 s because at 30 s you
start to hear the swell pattern repeat once you're lying still, and `thunder`
is 60 s so its rolls stay sparse. Seams are hidden by generating an extra 2 s
and equal-power blending that overrun back over the head, which makes the
sample after the last one literally the one that followed it in the source.

## Roadmap hooks already in the code

- `category` field supports switching the grid to Leanback-style category rows
  (worth doing if the catalog grows past ~12).
- Ken Burns on artwork: wrap `AssetImage` in an infinite `animateFloat` scale.
  Weigh it against the fact that the screen now blacks itself out after a
  minute — drifting motion mostly plays to an empty room.
