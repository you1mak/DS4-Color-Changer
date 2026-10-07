# PS4 LED Controller

Android root app for controlling the RGB LED of a DualShock 4 using the Linux sysfs LED interface.

## What it does

- Visual HSV color picker.
- Shows RGB and HEX values.
- Applies RGB through `su` / KernelSU.
- Automatically finds the current LED path using:
  `/sys/class/leds/0005:054C:05C4.*`
- Does not hard-code the changing suffix such as `0016` or `0018`.
- Optional background monitor re-detects the LED after reconnect and reapplies the saved color.
- Starts again after boot when Auto Apply was enabled.

## Required

- Root access through `su` (KernelSU/Magisk/etc.).
- DualShock 4 exposing the LED entries under the expected sysfs prefix.

## Build with GitHub Actions

Push the repository to GitHub, open **Actions → Build APK**, then download the `PS4-LED-Controller-debug` artifact.

## Notes

The monitor checks every second while enabled. It only writes the LED values when a new controller LED base path is detected, so it does not continuously spam the sysfs files.
