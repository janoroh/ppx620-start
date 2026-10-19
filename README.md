# PPX620 Start

A helper app for the **Philips PicoPix Max (PPX620)**, the Android 9 projector.

> This is an unofficial community project. It is not affiliated with or endorsed by Philips or Screeneo
> Innovation. Use it at your own risk.

## Features

### Startup that just works

About 30 seconds after you turn the projector on, its video chip switches to HDMI on its own. If your HDMI
device is asleep, you end up staring at the yellow "No Signal" screen. Press the wrong button at that moment and
the remote can stop responding altogether.

PPX620 Start watches the boot sequence and lets you choose what happens next:

- **HDMI** (default): stay on the HDMI input.
- **Launcher:** go back to the Android home screen as soon as the video chip allows it. That's about 13 seconds
  after the chip starts up.

**Remote unblocking** is on by default. If the remote gets stuck, the app notices and switches the picture
back. Timing matters here: the video chip ignores commands that come too early, and every ignored command makes
it wait longer. So the app waits until the chip is actually ready.

### Color calibration

- **Your color profile, applied at every startup:** red, green and blue LED current plus the "look" setting.
  The values are written exactly the way the Screeneo color app writes them.
- **Works in every energy mode.** Changing the energy mode, or plugging in or unplugging the power supply,
  resets the LEDs to factory values. The app catches this and restores your profile right away:
  - **Economy:** your profile as is.
  - **Standard:** 25% brighter, same color balance.
  - **Presentation:** as bright as possible without changing the color balance (red at maximum).
- **A separate battery profile.** On battery power, the same values come out noticeably red/magenta, so battery
  mode gets its own profile. You don't need to set it up: the app works it out from your mains profile. It uses
  about half the red and blue current, the same green, and look 1, the look the projector itself uses on
  battery. If colors on battery still look off, unplug the power supply and fine-tune the sliders. From then
  on, your own battery values are used.
- **Three profile slots** to save and restore your settings.
- **Import from the Screeneo color app:** copy its profiles 1 and 2, or whatever values are currently active.

### Test patterns

- **Always available:** color bars, gray steps, a 24-patch color chart, an alignment grid, black and white.
- **Screeneo test photos:** if the Screeneo color app is installed, PPX620 Start shows its original test
  photos, loaded straight from that app. A calibration then looks identical in both apps.
- **Built-in photos:** without the Screeneo app, 14 photos from Unsplash with similar subjects are used instead.

The app comes in English and German and follows your system language.

## Requirements

- A Philips PicoPix Max **PPX620** running the Android 9 firmware. Tested with build `PQ2A.190205.003`
  (July 2021) and version 1.2.1 of the Screeneo color app.
- The startup and color features rely on hardware interfaces specific to this projector
  (`/sys/class/dlpc343x` and the input source menu). On any other device, the app won't do anything useful.
- The Screeneo color app is optional. Without it, the built-in photos are used and the import buttons are hidden.

## Installation

1. Download `ppx620start-<version>.apk` from the [Releases](../../releases) page.
2. Copy it to the projector, e.g. on a USB stick, and open it in a file manager. If Android asks, allow
   installs from that source.
3. Open **PPX620 Start** once from the app list. After that, it runs automatically every time the projector
   starts.

To update, just install the new APK over the old one. Your settings and profiles are kept.

## Usage

Everything works with the remote:
- **Right panel:** settings, color sliders (press left/right to change a value), profile slots and status.
- **Left area:** the current test pattern. Switch patterns with ◀ ▶. Use **Full screen** to fill the whole
  picture.

**Default profile:** out of the box, the app uses red 650, green 110, blue 330 and look 14 on mains power. That's
the author's own calibration in Economy mode, and it corrects the greenish factory colors. Feel free to change
it. To leave colors entirely to the projector or the Screeneo app, turn the color feature off.

Suggested way to calibrate:
1. **Economy mode, on mains:** adjust the sliders until the test photos look right to you.
2. **Save the result** to a slot.
3. **On battery:** if something looks off, unplug the power supply and fine-tune the battery profile.

## What the app changes

- **LED color values only:** `redcurrent`, `greencurrent`, `bluecurrent` and `looks`, the same settings the
  Screeneo color app uses.
- **Startup and unblocking:** it sends the projector's own "HDMI status" broadcast and opens the projector's own
  input menu.
- **Nothing else:** no firmware changes, no system settings, and no fan control. Fan speed stays under the
  projector's own temperature control.
- **Log file:** stored in `Android/data/io.github.janoroh.ppx620start/files/protokoll.txt`. You can also view it in
  the app.

Uninstalling the app removes it completely. The projector then behaves exactly as it did before.

## Building from source

The project builds without Gradle; see `build.sh`. You'll need Windows with Git Bash, JDK 17 and the Android SDK
(build-tools plus an `android.jar`). Paths and the signing key are configured through environment variables,
documented at the top of the script.

## Credits

- Test photos from [Unsplash](https://unsplash.com), used under the Unsplash License. Photographers are listed
  in `assets/testbilder/CREDITS.txt` and under "Image credits" in the app.
- The 24-patch color chart uses the published sRGB reference values of the ColorChecker Classic.

## License

The source code is released under the MIT License (see `LICENSE`). The test photos are covered by the Unsplash
License, not the MIT License.
