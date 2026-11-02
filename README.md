# LineageOS on a fake "S26"

How a cheap clone sold as an "S26" became a privacy-focused phone.
It has:

- a boot animation built from the phone's **real** boot log;
- the GPS disabled in hardware;
- a system-level fake location.

> These scripts write straight to the phone's partitions (`logo`, `vendor`, `cache`).
> They are made for **this** hardware. On any other device they can leave it unbootable.
> Always back up first (see [Backup and restore](#backup-and-restore)).

## The real hardware

The phone claims flagship specs.
These are the real ones, read from the kernel
rather than from Android's spoofed properties:

| | Claimed | Real |
|---|---|---|
| SoC | — | MediaTek **MT6755** (Helio P10), 8× Cortex-A53 |
| GPU | — | Mali-T860 |
| RAM | lots | **3 GB** (`/proc/zoneinfo`) |
| Storage | lots | **32 GB** eMMC |
| Display | — | 720×1560 |
| Cameras | — | imx190, imx135, sp2508 |
| Touch / charging | — | ILITEK, ETA6947, PMIC MT6351 |

## Features

- **LineageOS 18.1** (Android 11), phh Treble GSI, rooted (`phh-su`).
- **Bootloader logo** and **new style charging screens**, redrawn inside the `logo` partition.
- **Two-part boot animation built from the real log.** The phone uses full-disk encryption (FDE),
  so it boots twice: once before the PIN and once after it. Each half shows a different part of
  the log, and no line is invented or repeated.
  - **Before the PIN:** kernel and init, up to the `CryptKeeper` screen.
  - **After the PIN:** Zygote and system_server, with a rounded progress bar, up to
    `BOOT_COMPLETED`. The mountains behind the bar are blurred so it stays readable.
- **GPS off in hardware.**
  - The `mnld` and `agpsd` daemons are disabled.
  - The driver node `/dev/stpgps` is made unreadable.
  - Google's Wi-Fi/cell network location is turned off.
- **System fake location.** A persistent system app in `/vendor/app` replaces the `gps`,
  `network` and `fused` providers with a fixed position in Reykjavík. Apps that need a location
  still work, and the chip is never turned on.
- **All audio through the earpiece.** This unit's loudspeaker is dead: there is no external
  amplifier on the I²C bus and every speaker path is silent. Every "speaker" path now drives the
  earpiece instead.

## Layout

```
tools/      PC-side Python: boot logo, boot animation, log filtering, sparse conversion
bootanim/   real boot logs of the two boot phases, already filtered
android/    sources of the rounded-corner overlay and the fake-location app, plus build.ps1
device/     scripts run on the phone as root (phh-su)
device/vendor/zz_oem_bootanim.rc   final init script installed in /vendor/etc/init
```

## Procedure

1. **Unlock the bootloader** and get into fastboot. On Windows, fastboot needs the
   *Android Bootloader Interface* driver from Google's USB driver (`android_winusb.inf`).
   To install it, go to *Device Manager → Update driver → Let me pick → Show all devices →
   Have disk*.
2. **Back up** the stock partitions with `device/backup.sh`.
3. **Flash the GSI.**
   - The partition layout is legacy A-only, but the phone needs the **AB** (system-as-root)
     image `lineage-18.1-20240121-UNOFFICIAL-arm64_bgS` from
     [andyyan-gsi on SourceForge](https://sourceforge.net/projects/andyyan-gsi/files/lineage-18.x/).
   - fastboot 37 fails with `bad_alloc` on large raw images, so convert the image first with
     `tools/img2simg.py`.
   - Then run `fastboot flash system` and `fastboot -w`.
4. **Logo and charging screens.**
   - `tools/make_logo.py` builds `logo_new.bin` from the stock `logo.img`.
   - The MTK format is a 512-byte header (magic `0x58881688`) followed by 80 zlib-compressed
     BGRA entries. Every new entry must be exactly as long as the stock one.
   - Write the result to the `logo` partition with `dd`.
5. **Boot animation.**
   - `tools/real_log.py --split` cuts `dmesg` + `logcat` into the two boot phases. It removes
     SSIDs, MAC addresses, IMEI and serial numbers.
   - `tools/make_bootanim.py` builds the two `bootanimation.zip` files: use `--bar=none` before
     the PIN and `--bar=0,1` after it.
   - The animations live on the `cache` partition, reformatted as ext4 and mounted as `/oem`
     (`device/oem_via_cache.sh`, `device/oem_rc.sh`, `device/oem_update.sh`).
     - `media/` holds the pre-PIN animation.
     - `media2/` holds the post-PIN one.
   - At `vold.decrypt=trigger_post_fs_data`, the init script bind-mounts `media2` over `media`.
6. **Privacy and corners.**
   - `android/build.ps1` builds both APKs. It needs Build-Tools 30.0.3 and the API 30
     `android.jar`; the SDK path is set at the top of the script.
   - Then run `device/privacy_corners.sh`, `device/gps_lock.sh` and
     `device/fakeloc_system.sh`.
   - After the reboot, run `appops set it.breachato.fakeloc android:mock_location allow`.
7. **Audio.** Run `device/earpiece_all.sh`. To undo it, run `device/spk_revert.sh`.

**Writing to `/vendor`:**
- Remount it read-write first: `mount -o remount,rw /vendor`.
- Afterwards, restore each file's SELinux label with `chcon`, copying it from the original file.
  Otherwise init ignores the file.

## Backup and restore

`device/backup.sh` saves `logo`, `boot`, `recovery`, `vendor`, `lk`, `lk2` and `odmdtbo` to
`/data/local/tmp/bk`. The stock firmware is **not** in this repository: it is proprietary and
about 2 GB. Keep your own copy somewhere safe. To go back to stock, run
`fastboot flash <partition> <file>.img`.

## Known limitations

- **Two boots.** This comes from full-disk encryption, which is left untouched. The two halves of
  the animation show different things.
- **No finger scrolling in the boot animation.** It runs before touch input exists.
- **Loudspeaker.** Broken in hardware on this unit.
- **Mock flag.** The fake location is marked as *mock*, so apps that check for it can tell.
- **Left as they are:** the bootloader's ORANGE state and the emergency-call button.

## License

Proprietary, all rights reserved: see [`LICENSE.md`](LICENSE.md). Third-party components are
listed in [`NOTICE.md`](NOTICE.md) and are not included.
