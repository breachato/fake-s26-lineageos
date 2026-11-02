# Third-party notices

The Project's own files are covered by [`LICENSE.md`](./LICENSE.md). The components below are
**not** part of this repository and are **not** covered by it: each one keeps its own license and
terms. They are listed because the Project builds on them, runs alongside them, or modifies a
device that contains them.

| Component | What it does here | License / terms |
|---|---|---|
| [LineageOS](https://lineageos.org/) 18.1 | The operating system installed on the device | Apache 2.0 and others (per source file) |
| [AndyYan's LineageOS GSI](https://sourceforge.net/projects/andyyan-gsi/) (`arm64_bgS`, built on phhusson's Treble GSI) | The system image flashed to the device; provides `phh-su` root | As published by their authors (Apache 2.0 / GPL components) |
| Android Open Source Project | Framework the overlay and the fake-location app are built against (`android.jar`, API 30) | Apache 2.0 |
| Android SDK Build-Tools 30.0.3 (`aapt2`, `dx`, `zipalign`, `apksigner`) and Platform-Tools (`adb`, `fastboot`, `mke2fs`) | Build the APKs; talk to and flash the device | Android Software Development Kit License Agreement |
| Google USB Driver (`android_winusb.inf`) | Windows driver for fastboot | Android SDK License Agreement |
| MediaTek MT6755 vendor firmware (`vendor`, `lk`, `logo`, `boot`, ...) | Stock firmware on the device; partly modified on the device by the scripts | Proprietary, MediaTek and the device manufacturer |
| [Pillow](https://python-pillow.org/) | Image rendering in `tools/` | MIT-CMU (HPND) |
| Python | Runs `tools/` | PSF License |
| Consolas font (`consola.ttf`) | Boot-log font, read from the local Windows install, not redistributed | Microsoft font license |

The boot logs in `bootanim/` are output captured from the device, filtered to remove network
names, MAC addresses, IMEI and serial numbers.

Galaxy and S26 Ultra are trademarks of Samsung Electronics. Android is a trademark of Google LLC.
The Project is not affiliated with Samsung, Google, MediaTek, or the LineageOS project.
