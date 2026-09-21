# MeetAgain App

[![CI](https://github.com/xuedi/meetagain-app/actions/workflows/ci.yml/badge.svg)](https://github.com/xuedi/meetagain-app/actions/workflows/ci.yml)
[![Version](https://img.shields.io/badge/Version-0.1.0-31c754.svg)](https://github.com/xuedi/meetagain-app/releases)
[![License](https://img.shields.io/badge/License-EUPL_v1.2-31c754.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0+-31c754.svg)](https://developer.android.com/about/versions/oreo)

The Android companion app for [MeetAgain](https://meetagain.org), the European,
privacy-first community platform for groups that meet in person.

The app is for members. It shows the next meetings of their groups, lets them sign up and talk
about an event, and tells them when something they care about changes. It carries the same
promise as the website: no tracking, no ads, no algorithmic feed, no lock-in.

## Status

Early development. The skeleton is in place: the build, the API client, the theme, all five
languages (English, German, Chinese, French, Spanish), CI, and a start and an About screen. The
member features come next.

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
[release](https://github.com/xuedi/meetagain-app/releases). `just version X.Y.Z` sets the app version
and the badge above together; `just check` fails when they disagree.

## License

[EUPL-1.2](LICENSE), like MeetAgain itself.
