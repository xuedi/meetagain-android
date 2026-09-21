# MeetAgain App

[![CI](https://github.com/xuedi/meetagain-android/actions/workflows/ci.yml/badge.svg)](https://github.com/xuedi/meetagain-android/actions/workflows/ci.yml)
[![Version](https://img.shields.io/badge/Version-0.1.0-31c754.svg)](https://github.com/xuedi/meetagain-android/releases)
[![License](https://img.shields.io/badge/License-EUPL_v1.2-31c754.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0+-31c754.svg)](https://developer.android.com/about/versions/oreo)

The Android companion app for [MeetAgain](https://meetagain.org), the European,
privacy-first community platform for groups that meet in person.

The app is for members. It shows the next meetings of their groups, lets them sign up and talk
about an event, and tells them when something they care about changes. It carries the same
promise as the website: no tracking, no ads, no algorithmic feed, no lock-in.

## Status

In development, not yet released to the public. The member features are in place:

- **Meetings:** the member's next meetings, yes or no with guests, the event conversation with
  photos, who is coming, and adding a meeting to the phone's calendar.
- **Groups:** browsing public groups and events, the member's own groups, invitations, joining and
  leaving.
- **People:** direct messages, member pages, following and blocking.
- **Notifications:** the website's notification list and settings, push through UnifiedPush or a
  regular check for phones without a push app, and meetagain.org links that open in the app.
- **Privacy and security:** sign-in with a device token stored encrypted on the phone, an optional
  app lock with fingerprint or screen PIN, and read-only access to the last synced content when
  offline.
- **Languages:** English, German, Chinese, French and Spanish.

Still to come: the Town Hall (forum and gallery), a closed beta, store listings and F-Droid.

## Stack

Kotlin with Jetpack Compose and Material 3, AndroidX only. The app runs without Google Play
Services and ships no analytics, tracking or advertising code.

## Development

Needs a JDK 17, the Android SDK (with `ANDROID_HOME` set) and an emulator or a phone with USB
debugging. The toolchain runs on the host.

- `just` lists the available commands.
- `just run` installs and starts the debug build. It talks to a local MeetAgain at
  `http://localhost:8000`, reached from the emulator or a USB phone through
  `adb reverse tcp:8000 tcp:80`. `just run -Pmeetagain.baseUrl=https://meetagain.org` uses
  production instead. Release builds talk to `https://meetagain.org` only.
- `just check` runs everything CI runs: formatting, lint, the tests and screenshot tests, the
  check for untranslated text, and a check that the release build contains no Google Play
  Services, analytics or tracking code.
- `just screenshots` re-records the reference screenshots after an intended UI change.
- `just api-refresh` updates `api/openapi.json`, the copy of the server's API description that
  the contract test checks the app against.

## Releases

Each `vX.Y.Z` tag builds a signed APK and attaches it, with its SHA-256 checksum, to the matching
[release](https://github.com/xuedi/meetagain-android/releases). `just version X.Y.Z` sets the app version
and the badge above together; `just check` fails when they disagree.

## License

[EUPL-1.2](LICENSE), like MeetAgain itself.
