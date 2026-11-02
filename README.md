# LineageOS on a fake "S26"

How a cheap clone sold as an "S26" became a privacy-focused phone.
It has:

- a boot animation built from the phone's **real** boot log;
- the GPS disabled in hardware;
- a system-level fake location;
- every connection through WireGuard, with a new Wi-Fi MAC and a different server each time;
- Google services, the cellular modem and system notifications switched off.

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
  `network` and `fused` providers with a position you choose. Apps that need a location still
  work, and the chip is never turned on.
  - A settings-style page lets you pick one of 23 cities or type custom coordinates; the change
    applies immediately.
- **Battery and charging.** A persistent system app (`/vendor/app/ChargeAnim`), signed with the
  platform key, does two things:
  - It draws the status-bar battery in place of SystemUI's: a horizontal battery with the
    percentage inside, and a bolt next to it while charging.
    - The icons are set through `IStatusBarService.setIcon` in two slots added at the end of
      `config_statusBarIcons`.
    - SystemUI's own battery is hidden through `icon_blacklist`.
  - When a charger is plugged in while the phone is locked, it shows a full-screen animation:
    a big battery that fills green up to the real level, with a bolt and the percentage counting
    up. It never shows on top of an app in use.
- **Tunnel.** Every connection goes through WireGuard, and nothing leaves outside it.
  - The official WireGuard app is always-on with lockdown and holds 8 server configurations.
  - A root daemon (`breachato-tunneld`, started by init) handles each connection:
    - It sets a new random Wi-Fi MAC when Wi-Fi turns on and after every real disconnection
      (carrier off for 4 s), so each connection gets a different MAC and DHCP address.
    - On every connection it brings the tunnel down and up again. It is fixed to `Cloudflare`;
      with `FIXED=` left empty it rotates among all the servers instead.
    - It reads sysfs with shell builtins and rewrites its status file only when something changes,
      so it costs about one process per second.
  - WireGuard is on the power-save whitelist, so it may start its VPN service from the daemon's
    intents while in the background.
  - The **Tunnel** app shows network, MAC, IP and server, and connects to Wi-Fi networks. It also
    has *New identity*, *Change server* and *Check public IP*.
- **App lock.** The apps you choose ask for a code every time they are opened.
  - An accessibility service shows a passcode screen on top of the protected app: frosted
    wallpaper, the app's icon and name, a row of dashes and bare numerals (no key shapes) that
    swell and turn lavender when pressed.
  - The app opens the moment the right code is typed, without OK. A wrong full-length entry turns
    the dashes red and shakes them. The first use asks you to choose a code.
  - Opening the app lock from the home screen asks for the code, then shows its settings: a master
    switch, a switch per app, the session state and *Change code*.
  - **Trusted session:** after 3 correct codes in one screen-on session, each at least 20 seconds
    after the previous one, no code is asked until the screen turns off.
  - The code is stored only as a salted SHA-256 hash, and five wrong attempts lock entry for
    30 seconds.
- **Home, lock screen and system UI.**
  - A custom launcher (`/vendor/app/Home`): pages of rounded icons with labels, page dots, a search
    pill and a frosted dock. Pull down to search; long press for dock / app info / uninstall.
  - A custom lock screen raised as the screen turns off, above the keyguard. It shows, centred,
    a lock glyph, the date and a large clock, plus a charging pill and torch / camera buttons
    (the camera opens in secure mode). Swiping up hands over to Android's PIN, which stays the
    disk-encryption credential.
  - A SystemUI overlay gives the quick settings rounded-square buttons and a translucent rounded
    sheet, makes notifications dark and translucent, and shortens the drag needed to open and
    close the shade. The system accent is lavender and dark theme is on.
  - The custom apps share `android/common`: Geist / Geist Mono fonts and the glass and
    settings-style building blocks.
- **Performance.**
  - While the screen is on, the daemon keeps all eight cores online. MediaTek's power HAL
    otherwise rewrites the hotplug limits after boot and on screen events, even down to zero big
    cores. Thermal limits are untouched.
  - Animations run at 0.5x.
  - User-installed apps are in the *restricted* standby bucket and may not run in the background.
    WireGuard is the exception. At most 16 cached processes are kept.
- **Wave to wake** (instead of double tap). Two quick passes of a hand over the proximity sensor
  turn the screen on. Covering the sensor for long, as in a pocket, does nothing.
  - Double tap cannot work on this unit. The ILITEK controller (chip 79505) is a TDDI chip, shared
    with the display, and is powered down with it. Its driver does switch to gesture mode, but
    every I2C command fails, so gesture mode is left off (`device/dt2w_off.sh`).
  - The proximity sensor is a wake-up sensor and is only listened to while the screen is off.
- **Tracking off.**
  - Google Play Services, GSF and the other Google background packages are disabled.
  - Airplane mode keeps the cellular modem off while Wi-Fi stays on. Emergency calls still turn
    the radio back on.
  - Bluetooth, Wi-Fi/BLE background scanning, captive-portal probes and LineageOS stats are off.
  - Notifications from the system packages are blocked, including the ones Settings won't let you
    block (`android/notifctl`).
  - The emergency-alert receiver is disabled.
- **Navigation.** Gesture navigation, with the back gesture on the left edge only.
- **All audio through the earpiece.** This unit's loudspeaker is dead: there is no external
  amplifier on the I²C bus and every speaker path is silent. Every "speaker" path now drives the
  earpiece instead.

## Layout

```
tools/      PC-side Python: boot logo, boot animation, status-bar battery icons, log filtering,
            sparse conversion
bootanim/   real boot logs of the two boot phases, already filtered
android/    overlays (corners: framework-res, sysui: SystemUI), shared UI and fonts (common),
            launcher + lock screen (home), fake-location app, charge/battery app, app lock,
            Tunnel app and its root daemon (tunnel/breachato-tunneld.sh), notification tool
            (notifctl), plus build.ps1
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
6. **Apps.**
   - `android/build.ps1` builds the overlay and the apps. It needs Build-Tools 30.0.3 and the
     API 30 `android.jar`; the SDK path is set at the top of the script.
     - The apps are compiled with `dx`, without desugaring, so the Java sources avoid lambdas.
   - The charge app and Tunnel are signed with the **platform key**. This GSI's system is signed
     with AOSP's public test key (`build/target/product/security/platform.*`), which grants them
     `STATUS_BAR` and `NETWORK_SETTINGS`.
     - Put `platform.pk8` and `platform.x509.pem` in the SDK folder's `platformkey/`; they are not
       committed.
     - A system signed with public test keys means any app can be signed as "system".
   - Install with `device/privacy_corners.sh`, `device/gps_lock.sh`,
     `device/fakeloc_system.sh` and `device/chargeanim_system.sh`.
   - After the reboot, run `appops set it.breachato.fakeloc android:mock_location allow`.
   - If a re-signed system app keeps old permissions, delete `/data/system/package_cache/*` and
     reboot.
7. **Tunnel and privacy** (as root):
   - `device/harden.sh` disables the Google packages, enables airplane mode with Wi-Fi on and
     turns off the probes and scans.
   - `device/notif_block.sh` blocks system notifications through `android/notifctl`.
   - `device/cb_off.sh` and `device/cb_disable.sh` switch off emergency alerts.
   - Install WireGuard from [download.wireguard.com](https://download.wireguard.com/android-client/).
   - `device/wg_import.sh` imports the `.conf` files into its data folder, together with a
     DataStore file that turns on remote control, restore on boot and the first tunnel.
   - `device/install_tunnel.sh` sets always-on VPN with lockdown and installs Tunnel and App lock
     as system apps. It also installs the daemon and its init service
     (`seclabel u:r:phhsu_daemon:s0`), and enables the lock's accessibility service.
8. **Status bar and gestures** (as root):
   - Gesture navigation:
     `cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural`
   - No back gesture on the right edge: `settings put secure back_gesture_inset_scale_right 0`
9. **Audio.** Run `device/earpiece_all.sh`. To undo it, run `device/spk_revert.sh`.

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
- **Wi-Fi MAC.** The MediaTek driver reads its NVRAM MAC only at boot, and Android switches Wi-Fi
  off if `wlan0` is taken down. So the daemon changes the MAC live, with `ip link set address`,
  while the interface is not associated. Android never resets it, because
  `config_wifi_connected_mac_randomization_supported` is false on this build.
- **No internet without the tunnel.** With lockdown on, nothing works until Wi-Fi is connected and
  a WireGuard server is up.
- **Left as they are:** the bootloader's ORANGE state and the emergency-call button.

## License

Proprietary, all rights reserved: see [`LICENSE.md`](LICENSE.md). Third-party components are
listed in [`NOTICE.md`](NOTICE.md) and are not included.
