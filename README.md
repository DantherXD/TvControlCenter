# TvControlCenter

An Android TV Control Center with the look, feel, and behavior of the
Apple TV (tvOS) Control Center.

## Features
- Translucent rounded panel that slides in from the right, dimmed scrim
- tvOS-style focus: white pill highlight, dark glyphs, springy scale animation
- User profile row, Sleep row, Now Playing card
- Quick toggles: Wi-Fi, Bluetooth, Focus (Do Not Disturb), Airplane,
  Screensaver, Power
- Live clock + date in the panel footer
- Full D-pad navigation: RIGHT on the rightmost item closes the panel,
  BACK or MENU closes it too

## Build
Every push triggers GitHub Actions, which builds `app-debug.apk`
(see the Actions tab -> latest run -> Artifacts).

Local build (desktop): `./gradlew assembleDebug` (JDK 17, Android SDK 34).

## Install on a TV
