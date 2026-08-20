# Drift — Privacy Policy

**Effective date:** 20 August 2026
**Applies to:** the Drift Android TV app (`com.drift.tv`, `com.drift.tv.store`), version 0.2.0 and later.

Drift is an Android TV app that plays looping ambient sleep sounds. It has no
accounts, no sign-in, no ads, and no in-app purchases. It never asks you for
your name, email, address, phone number, or payment details, because it has
nowhere to put them.

This document describes exactly what leaves your device, what stays on it, and
how to switch off the parts that are optional.

---

## Two editions of Drift, and why it matters here

Drift is built in two variants, and they differ in one privacy-relevant way.
Check which one you have in **Settings → Apps** on your TV:

| | **Store edition** (`com.drift.tv.store`) | **Sideload edition** (`com.drift.tv`) |
|---|---|---|
| Where it comes from | Google Play / Amazon Appstore | GitHub Releases, direct APK install |
| Sleep sounds, timer, lights-out | Yes | Yes |
| Optional analytics | Yes | Yes |
| **Web Indexing (bandwidth sharing)** | **Not present at all** | Present, **off until you opt in** |

In the store edition the Web Indexing component is not disabled — it is
**absent**. The software is not compiled into that build, so none of
[section 3](#3-web-indexing-bandwidth-sharing--sideload-edition-only-opt-in)
applies to it. This is verifiable: the store build does not even request the
`RECEIVE_BOOT_COMPLETED` or `FOREGROUND_SERVICE_DATA_SYNC` permissions that
component requires.

---

## 1. Information stored only on your device

Drift saves a small amount of state so you don't have to rebuild your setup
every night. This is written to Android's private app storage, is readable only
by Drift, and is **never transmitted anywhere**:

- The volume you last set for each individual sound.
- The sounds in your last mix, so "Resume last mix" works.
- Your last sleep-timer length.
- A flag recording that the Web Indexing consent screen has already been shown
  once, so it isn't shown on every launch.

All of it is deleted when you uninstall Drift or use **Clear data** in Android's
app settings.

## 2. Analytics

Drift can send anonymous product analytics to
[PostHog](https://posthog.com/privacy), which we use to understand which sounds
and features people actually use. Analytics are **not** tied to any identity —
there is no account to tie them to. PostHog assigns a random identifier to the
installation; it is not your name, email, or device serial number, and it is
reset by clearing app data or reinstalling.

**Events Drift sends, in full:**

| Event | What it records |
|---|---|
| Application opened / backgrounded | App start and session boundaries |
| Screen viewed | Which of the three screens: `home`, `player`, `settings` |
| `sound_played` | Which sound and its category |
| `layer_added` / `layer_removed` | Which sound, and how many layers are mixed |
| `sleep_timer_set` / `sleep_timer_cancelled` | Timer length in minutes |
| `mix_resumed` | Which sounds were in the resumed mix |
| `lights_out_triggered` | Whether lights-out was manual or from idle |

That is the complete list. There are no hidden events.

**What PostHog additionally records**, as is standard for analytics services:
technical details of your device and app (model, manufacturer, Android version,
app version, language, timezone, screen size), and your IP address, from which
an approximate location — typically country or city — is derived. PostHog
receives this because any internet request necessarily reveals an IP address.
Data is sent to PostHog's **US region** servers.

**Session replay is deliberately disabled.** Drift is a sleep app whose screen
is black by design for hours at a time; recording that would be both useless and
needlessly invasive, so the feature is switched off in code rather than merely
left unused.

Analytics may be absent entirely from your build: they are only active if the
build was configured with an analytics key. Builds produced from a fresh clone
of the source, and the automated builds published by this project's CI, have
analytics switched off.

## 3. Web Indexing (bandwidth sharing) — sideload edition only, opt-in

The sideload edition includes **Bright SDK** by Bright Data, offered under the
name **Web Indexing**. When enabled, it uses a portion of your device's spare
network bandwidth and its IP address to download publicly available web pages on
behalf of Bright Data's vetted business customers. This is how the app is funded.

**It is off unless you turn it on.** Nothing is shared until you have seen
Bright Data's own consent screen and pressed **I Agree** there. Dismissing
Drift's introductory screen, pressing Back, or choosing **No thanks** all record
a refusal, and nothing is shared.

Required disclosure, per Bright Data:

> In return for keeping 'Drift' free to use, you may choose to be a peer on the
> Bright Data network. By doing so, you agree to have read and accepted the
> [Bright SDK EULA](https://bright-sdk.com/EULA) and
> [Bright Data's Privacy Policy](https://bright-sdk.com/privacy-policy).
>
> You may opt out of the Bright Data network at any time by opening **Drift →
> Settings → Web Indexing** and choosing **Turn off**. The change takes effect
> immediately.

**What Bright Data receives:** your IP address and the approximate location
derived from it, plus the web traffic it routes through your connection. Per
Bright Data, no other personal information is collected and they do not track
you. Their own policy, linked above, governs what they do with it — Drift has no
access to that data and does not receive it.

**What this costs you:** it consumes network data and a small amount of power,
and it can affect your connection speed. Avoid it on a metered, capped, or
otherwise restricted connection. While enabled, the component keeps running in
the background even when Drift is closed, and starts again after the device
reboots or the app updates — that persistence is why it needs the
`RECEIVE_BOOT_COMPLETED` permission. Turning it off in Settings stops that
entirely.

You can learn more at
[bright-sdk.com/users](https://bright-sdk.com/users#learn-more-about-bright-sdk-web-indexing),
also reachable via the QR code shown in Drift's Settings screen.

## 4. What Drift never does

- No accounts, sign-in, or personal details collected.
- No advertising, ad networks, or ad identifiers.
- No microphone, camera, contacts, or precise-location access — Drift does not
  hold those permissions, so it cannot use them.
- No session or screen recording.
- No selling of personal information.
- No tracking across other apps or websites.

## 5. Permissions Drift requests, and why

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Analytics; and Web Indexing in the sideload edition |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep audio playing with the screen off, with a visible notification |
| `WAKE_LOCK` | Prevent the device sleeping mid-playback |
| `POST_NOTIFICATIONS` | The playback notification |
| `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE_DATA_SYNC` | **Sideload edition only** — lets Web Indexing resume after a reboot, once you have opted in |

## 6. Children

Drift is not directed at children and does not knowingly collect information
from them. It contains no ads, no social features, and no user-generated
content.

## 7. Your choices

- **Turn off Web Indexing:** Settings → Web Indexing → Turn off.
- **Erase everything Drift has stored:** Android Settings → Apps → Drift →
  Clear data, or simply uninstall.
- **Avoid the optional components entirely:** use the store edition, which
  contains no bandwidth-sharing software.
- **Requests about data already collected:** contact us (below). Note that
  analytics are anonymous, so we may be unable to locate records belonging to a
  specific person; requests concerning Bright Data's network should go to Bright
  Data directly, via their policy linked above.

## 8. Third-party services

| Service | Role | Their policy |
|---|---|---|
| PostHog | Product analytics | https://posthog.com/privacy |
| Bright Data (Bright SDK) | Web Indexing — sideload edition, opt-in only | https://bright-sdk.com/privacy-policy · [EULA](https://bright-sdk.com/EULA) |

## 9. Changes

Material changes to this policy will be published here with an updated
effective date. The full revision history of this document is public in this
repository's Git history.

## 10. Contact

Questions about this policy or about Drift's handling of data:
https://github.com/alexbrooks7/drift/issues
