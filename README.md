# Star Signs

An Android app for finding the zodiac constellations. Point your phone at the night sky and the star signs are drawn over the live camera view.

## Features

- All 12 zodiac constellations, with the one nearest the middle of the screen highlighted. Its card shows the sign's dates, brightest star, where it is in the sky, and any planets in it tonight.
- The Sun, Moon, Mercury, Venus, Mars, Jupiter and Saturn.
- Landmark constellations to get your bearings: the Plough, Cassiopeia, Orion and Polaris.
- The ecliptic, a horizon line with compass points, and an arrow pointing to the nearest star sign when none are on screen.
- **Manual alignment:** drag to line the drawing up with the real stars and pinch to zoom. Double-tap or tap **Re-align** to reset.
- Night mode (all red, to keep your night vision) and a camera-off mode for very dark skies.
- Tap any star to see its name and brightness.

## How it works

The app doesn't recognise stars in the camera image. It works out where each star should be from your location, the current time and the phone's orientation sensors (the rotation vector, corrected for magnetic declination). It then projects that position through a model of the camera's field of view. Phone compasses are often a few degrees off, which is why manual alignment is there.

The calculations live in `Astro.kt`:
- sidereal time and precession
- conversion from equatorial to horizontal coordinates
- JPL approximate orbital elements for the planets
- a low-precision lunar theory for the Moon

## Privacy

- The app has **no internet permission**, so it can't send anything anywhere.
- Your location and the camera feed are used on the device only, for the sky calculation and the live preview. They're never saved or shared.
- No accounts, analytics or ads.

| Permission | Why |
|---|---|
| Camera | Live preview behind the star overlay (optional: the app works without it) |
| Location | Works out which stars are above you (falls back to central Scotland if denied) |

## Install

Download the APK from [Releases](../../releases) and open it on your phone. You'll need to allow installs from unknown sources. Requires Android 8.0 or later, plus a compass and accelerometer. A gyroscope gives smoother tracking.

## Build

```
./gradlew assembleRelease
```

Requires JDK 17+ and the Android SDK (API 34). The release build is signed with the debug key so it installs straight away. Set up a proper keystore if you want to publish it on the Play Store.
