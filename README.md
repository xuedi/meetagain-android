# MeetAgain App

The Android companion app for [MeetAgain](https://meetagain.org), the European,
privacy-first community platform for groups that meet in person.

The app is for members. It shows the next meetings of their groups, lets them sign up and talk
about an event, and tells them when something they care about changes. It carries the same
promise as the website: no tracking, no ads, no algorithmic feed, no lock-in.

## Status

Pre-development. The stack is decided; the project scaffold comes next.

## Stack

Kotlin with Jetpack Compose and Material 3, AndroidX only. The app runs without Google Play
Services and ships no analytics, tracking or advertising code.

## Development

Needs a JDK 17, the Android SDK (with `ANDROID_HOME` set) and an emulator or a phone with USB
debugging. The toolchain runs on the host.

- `just` lists the available commands.
- A local MeetAgain is reached from the emulator or a USB phone through
  `adb reverse tcp:8000 tcp:80`, at `http://localhost:8000`. Release builds talk to
  `https://meetagain.org` only.

## License

Not decided yet. Until a license file exists in this repository, all rights are reserved.
