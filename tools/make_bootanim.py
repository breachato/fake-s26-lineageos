"""Build an Android bootanimation.zip: starry wallpaper fading in with a
kernel-style log that types out from the top-left and scrolls smoothly.

Usage: python make_bootanim.py WALLPAPER OUT_DIR WIDTH HEIGHT [--preview] [--log=FILE] [--bar=FROM,TO|none]
"""
import io
import math
import os
import random
import re
import sys
import zipfile

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

FPS = 25
FONT_PATH = r"C:\Windows\Fonts\consola.ttf"
MOUNTAIN_TOP = 1350 / 1671   # y of the highest peak in the source photo (fraction)
LK_DIM = 0.22                # brightness of the static bootloader logo
JPEG_Q = 80

# timeline (seconds)
FADE_IN = 1.6
PART0_LEN = 20.0             # intro + scrolling log (plays once)
STOP_LEN = 1.2               # scroll decelerates to rest at the end of part0
SCROLL_EASE = 1.3
STEADY_RATE = 3.6            # lines per second while scrolling
CURSOR_FRAMES = 12           # part1 loop: blinking cursor (looped parts stay in GPU memory)
FADE_OUT = 1.0

COL_TEXT = (214, 224, 240)
COL_DIM = (140, 156, 186)
COL_OK = (92, 222, 140)
COL_WARN = (240, 200, 90)


def kernel_lines():
    rnd = random.Random(26)
    t = 0.0
    out = []

    def k(msg, dt=None):
        nonlocal t
        t += dt if dt is not None else rnd.uniform(0.0004, 0.03)
        out.append(("k", f"[{t:12.6f}] {msg}"))

    k("Booting Linux on physical CPU 0x0", 0)
    k("Linux version 3.18.119 (lineage@treble) (gcc version 4.9.x) #1 SMP PREEMPT", 0)
    k("CPU: AArch64 Processor [410fd032] revision 2", 0)
    k("Machine model: MT6755V/C", 0)
    k("efi: Getting EFI parameters from FDT:")
    k("Reserved memory: created CMA memory pool at 0x00000000be000000, size 32 MiB")
    k("psci: probing for conduit method from DT.")
    k("psci: PSCIv1.0 detected in firmware.")
    k("PERCPU: Embedded 15 pages/cpu @ffffffc0bff6c000 s22016 r8192 d31232 u61440")
    k("Built 1 zonelists in Zone order, mobility grouping on.  Total pages: 772536")
    k("Kernel command line: console=tty0 root=/dev/mmcblk0p27 androidboot.hardware=mt6755")
    k("Memory: 2891264K/3145728K available (11264K kernel code, 1280K rwdata)")
    k("SLUB: HWalign=64, Order=0-3, MinObjects=0, CPUs=8, Nodes=1")
    k("Preemptible hierarchical RCU implementation.")
    k("NR_IRQS:64 nr_irqs:64 0")
    k("GIC: Using split EOI/Deactivate mode")
    k("Architected cp15 timer(s) running at 13.00MHz (phys).")
    k("sched_clock: 56 bits at 13MHz, resolution 76ns, wraps every 4398046511101ns")
    k("Calibrating delay loop (skipped), value calculated using timer frequency.. 26.00 BogoMIPS")
    k("pid_max: default: 32768 minimum: 301")
    k("Mount-cache hash table entries: 8192 (order: 4, 65536 bytes)")
    for c in range(1, 8):
        k(f"CPU{c}: Booted secondary processor [410fd032]")
    k("Brought up 8 CPUs")
    k("SMP: Total of 8 processors activated.")
    k("CPU features: detected feature: 32-bit EL0 Support")
    k("devtmpfs: initialized")
    k("NET: Registered protocol family 16")
    k("cpuidle: using governor menu")
    k("vdso: 2 pages (1 code @ ffffffc000e67000, 1 data @ ffffffc001314000)")
    k("hw-breakpoint: found 6 breakpoint and 4 watchpoint registers.")
    k("[PMIC] mt6351_probe: chip id 0x5140")
    k("mtk-msdc 11230000.msdc0: emmc: 32 GB HS400 mode")
    k("mmcblk0: mmc0:0001 32GB 29.1 GiB")
    k("mmcblk0: p1 p2 p3 p4 p5 p6 p7 p8 p9 p10 ... p28")
    k("ion: ion_mm_heap created")
    k("mali 13040000.mali: GPU identified as 0x0860 r2p0 status 0")
    k("[DISP] primary display 720x1560 @ 60fps")
    k("zram0: detected capacity change from 0 to 1073741824")
    k("EXT4-fs (mmcblk0p27): mounted filesystem with ordered data mode")
    k("Freeing unused kernel memory: 1024K")
    out.append(("k", "Run /init as init process"))

    init = [
        "init: init first stage started!",
        "init: Using Android DT directory /proc/device-tree/firmware/android/",
        "init: [libfs_mgr] Created logical partition system",
        "init: Switching root to '/system'",
        "init: init second stage started!",
        "init: Loading SELinux policy",
        "selinux: SELinux: Loaded file_contexts",
        "init: Parsing file /system/etc/init/hw/init.rc...",
        "init: processing action (early-init) from (/system/etc/init/hw/init.rc:12)",
        "ueventd: ueventd started!",
        "init: Starting service 'servicemanager'...",
        "init: Starting service 'hwservicemanager'...",
        "init: Starting service 'vold'...",
        "vold: Vold 3.0 (the awakening) firing up",
        "init: processing action (fs) from (/vendor/etc/init/hw/init.mt6755.rc)",
        "EXT4-fs (dm-0): mounted filesystem with ordered data mode",
        "init: Starting service 'logd'...",
        "init: Starting service 'keystore'...",
        "init: Starting service 'netd'...",
        "init: Starting service 'zygote'...",
        "init: Starting service 'zygote_secondary'...",
        "init: Starting service 'surfaceflinger'...",
        "init: Starting service 'bootanim'...",
        "init: Starting service 'audioserver'...",
        "init: Starting service 'cameraserver'...",
        "init: Starting service 'media'...",
        "init: Starting service 'wificond'...",
        "init: Starting service 'gatekeeperd'...",
        "zygote64: Preloading classes...",
        "zygote64: Preloading resources...",
        "zygote64: Preloading shared libraries...",
        "SystemServer: Entered the Android system server!",
        "SystemServerTiming: StartActivityManager",
        "SystemServerTiming: StartPowerManager",
        "SystemServerTiming: StartPackageManagerService",
        "PackageManager: Scanning /system/priv-app",
        "PackageManager: Scanning /product/app",
        "SystemServerTiming: StartWindowManagerService",
        "SystemServerTiming: StartInputManagerService",
        "SystemServerTiming: StartNetworkManagementService",
        "SystemServerTiming: StartConnectivityService",
        "SystemServerTiming: StartNotificationManager",
        "SystemServerTiming: StartWallpaperManagerService",
        "SystemServerTiming: StartAudioService",
        "SystemServerTiming: StartJobScheduler",
        "ActivityManager: System now ready",
        "SystemUI: Starting SystemUI services",
    ]
    oks = [
        "Started Hardware Service Manager.", "Mounted /data (ext4).",
        "Started Logging Daemon.", "Started Keystore.", "Started Network Daemon.",
        "Started Zygote.", "Started SurfaceFlinger.", "Started Audio Server.",
        "Started Media Codec Service.", "Reached target Local File Systems.",
        "Started Package Manager.", "Started Window Manager.",
        "Started Input Manager.", "Started Connectivity Service.",
        "Reached target Network.", "Started Notification Manager.",
        "Started Job Scheduler.", "Started SystemUI.",
    ]
    oi = 0
    for i, line in enumerate(init):
        t += rnd.uniform(0.02, 0.4)
        out.append(("k", f"[{t:12.6f}] {line}"))
        if i % 3 == 2 and oi < len(oks):
            out.append(("ok", oks[oi]))
            oi += 1
    while oi < len(oks):
        out.append(("ok", oks[oi]))
        oi += 1
    return out


def load_log(path, need):
    """Real boot log lines, thinned evenly so the last one shown is the last of the file."""
    rows = [l.rstrip("\n") for l in open(path, encoding="utf-8") if l.strip()]
    if len(rows) > need:
        idx = sorted({round(i * (len(rows) - 1) / (need - 1)) for i in range(need)})
        rows = [rows[i] for i in idx]
    return [("k", r) for r in rows]


def blur_mountains(bg, mountain_y):
    """Soften and slightly darken the mountains so the progress bar reads clearly over them."""
    w, h = bg.size
    soft = ImageEnhance.Brightness(bg.filter(ImageFilter.GaussianBlur(w / 80))).enhance(0.78)
    mask = Image.new("L", (1, h))
    y0, y1 = mountain_y - h * 0.05, mountain_y + h * 0.03
    for y in range(h):
        mask.putpixel((0, y), int(255 * ease((y - y0) / (y1 - y0))))
    return Image.composite(soft, bg, mask.resize((w, h)))


class Renderer:
    def __init__(self, wallpaper, w, h, log_path=None, bar=(0.0, 1.0)):
        self.w, self.h = w, h
        src = Image.open(wallpaper).convert("RGB")
        # cover the screen, keeping the mountains at the bottom edge
        s = max(w / src.width, h / src.height)
        sw, sh = round(src.width * s), round(src.height * s)
        src = src.resize((sw, sh), Image.LANCZOS)
        # lean right so the "BREACHATO" signature (right edge at 97% of the photo) stays in frame
        x0 = max(0, min(sw - w, round(sw * 0.985) - w))
        self.bg = src.crop((x0, sh - h, x0 + w, sh))
        mountain_y = MOUNTAIN_TOP * sh - (sh - h)
        self.mountain_y = mountain_y
        self.bg = blur_mountains(self.bg, mountain_y)
        self.bar = bar

        self.font = ImageFont.truetype(FONT_PATH, max(12, round(w / 46)))
        asc, desc = self.font.getmetrics()
        self.lh = asc + desc + 3
        self.cw = self.font.getlength("M")
        self.margin = round(w * 0.04)
        self.top = round(h * 0.035)
        self.bottom = int(mountain_y - h * 0.03)          # last baseline slot
        self.cap = (self.bottom - self.top) // self.lh     # visible lines
        self.fade_edge = self.lh * 2
        self.max_chars = int((w - 2 * self.margin) / self.cw)

        self._plan()
        # lines fully revealed by the end of part0 (the slot reached exactly at the end never types)
        need = self.cap + int(self.end_scroll // self.lh) - 1
        self.lines = load_log(log_path, need) if log_path else kernel_lines()
        if len(self.lines) < need:
            # short log: scroll slower so the last real line lands at the end, never repeat lines
            self.v *= (len(self.lines) - self.cap + 1) * self.lh / self.end_scroll
            self.end_scroll = math.floor(self._scroll_raw(PART0_LEN) / self.lh + 1e-6) * self.lh

    # --- timeline -------------------------------------------------------
    def _plan(self):
        rnd = random.Random(7)
        # fill phase: bursty kernel output until the text reaches the mountains
        self.reveal = []
        t = FADE_IN * 0.45
        for i in range(self.cap):
            self.reveal.append(t)
            t += rnd.choice([0.03, 0.04, 0.05, 0.08, 0.12, 0.2])
        self.t_full = self.reveal[-1]
        self.v = STEADY_RATE * self.lh                     # px/s
        # scroll ends exactly on a line boundary at the end of part0
        stop_t = PART0_LEN - STOP_LEN
        raw = self._scroll_raw(PART0_LEN)
        self.end_scroll = math.floor(raw / self.lh) * self.lh
        self.stop_t = stop_t

    def _scroll_raw(self, t):
        tau = t - self.t_full
        if tau <= 0:
            return 0.0
        if tau < SCROLL_EASE:
            return self.v * tau * tau / (2 * SCROLL_EASE)
        return self.v * (tau - SCROLL_EASE / 2)

    def scroll(self, t):
        if t <= self.stop_t:
            return self._scroll_raw(t)
        # smoothstep from the running scroll to the resting position
        a = self._scroll_raw(self.stop_t)
        u = min(1.0, (t - self.stop_t) / STOP_LEN)
        e = u * u * (3 - 2 * u)
        return a + (self.end_scroll - a) * e

    def line_reveal_time(self, i, frames_cache={}):
        if i < self.cap:
            return self.reveal[i]
        # the line appears once its slot has scrolled up to the bottom limit
        need = (i - self.cap + 1) * self.lh
        lo, hi = self.t_full, PART0_LEN + 1000
        if self.scroll(PART0_LEN) < need:
            return None
        for _ in range(40):
            mid = (lo + hi) / 2
            if self.scroll(mid) >= need:
                hi = mid
            else:
                lo = mid
        return hi

    # --- drawing --------------------------------------------------------
    def line_text(self, i):
        kind, txt = self.lines[i % len(self.lines)]
        if i >= len(self.lines):
            kind, txt = self.lines[len(self.lines) - 30 + (i - len(self.lines)) % 30]
        return kind, txt

    def frame(self, t, bg_alpha, text_alpha, cursor=None, final_line=None):
        img = ImageEnhance.Brightness(self.bg).enhance(bg_alpha)
        layer = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        sc = self.scroll(t)
        first = max(0, int(sc // self.lh) - 1)
        last_drawn = None
        for i in range(first, first + self.cap + 3):
            rt = self.line_reveal_time(i)
            if rt is None or rt > t:
                continue
            y = self.top + i * self.lh - sc
            if y < self.top - self.lh or y > self.bottom:
                continue
            kind, txt = self.line_text(i)
            if final_line is not None and i == final_line[0]:
                kind, txt = final_line[1]
            n = len(txt) if kind == "ok" else min(len(txt), int((t - rt) * 420) + 1)
            a = text_alpha * min(1.0, (t - rt) / 0.08)
            # fade lines out as they reach the top edge
            if y < self.top + self.fade_edge:
                a *= max(0.0, (y - self.top + self.lh) / (self.fade_edge + self.lh))
            if a <= 0.01:
                continue
            self._draw_line(d, y, kind, txt[: min(n, self.max_chars)], a)
            last_drawn = (i, y, kind, txt[:n])
        self._draw_bar(d, t, text_alpha)
        if cursor and last_drawn:
            i, y, kind, txt = last_drawn
            x = self.margin + self.cw * (min(len(txt), self.max_chars - 2) + 1)
            d.rectangle([x, y + 2, x + self.cw - 1, y + self.lh - 3],
                        fill=COL_TEXT + (int(230 * text_alpha),))
        img = img.convert("RGBA")
        img.alpha_composite(layer)
        return img.convert("RGB")

    def _draw_bar(self, d, t, a):
        """Rounded progress bar over the mountains; fills with the log, holds full while waiting."""
        if a <= 0.01 or self.bar is None:
            return
        bw = int(self.w * 0.52)
        bh = max(6, round(self.w / 90))
        x0 = (self.w - bw) // 2
        cy = int(self.mountain_y + (self.h - self.mountain_y) * 0.40)
        y0, y1 = cy - bh // 2, cy - bh // 2 + bh
        r = bh / 2
        start = FADE_IN * 0.7
        lo, hi = self.bar
        p = lo + (hi - lo) * ease((t - start) / (self.stop_t - start))
        d.rounded_rectangle([x0, y0 + 2, x0 + bw, y1 + 2], radius=r, fill=(0, 0, 0, int(90 * a)))
        d.rounded_rectangle([x0, y0, x0 + bw, y1], radius=r, fill=(255, 255, 255, int(46 * a)))
        fw = max(bh, int(bw * p))
        d.rounded_rectangle([x0, y0, x0 + fw, y1], radius=r, fill=(236, 242, 255, int(235 * a * min(1, p * 8))))
        # soft glow on the leading edge
        lx = x0 + fw
        d.ellipse([lx - bh - 2, y0 - 2, lx + 2, y1 + 2], fill=(255, 255, 255, int(110 * a * min(1, p * 8))))

    def _draw_line(self, d, y, kind, txt, a):
        x = self.margin
        sh = (0, 0, 0, int(170 * a))
        if kind == "ok":
            parts = [("[  ", COL_TEXT), ("OK", COL_OK), ("  ] ", COL_TEXT), (txt, COL_TEXT)]
        else:
            ts_end = txt.find("]") + 1 if txt.startswith("[") else 0
            body = txt[ts_end:]
            m = re.search(r"Starting service '[^']*'|BOOT_COMPLETED|Brought up \d+ CPUs|"
                          r"System now ready|WE ARE BOOTED!", body)
            if m:
                parts = [(txt[:ts_end], COL_DIM), (body[:m.start()], COL_TEXT),
                         (body[m.start():m.end()], COL_OK), (body[m.end():], COL_TEXT)]
            else:
                parts = [(txt[:ts_end], COL_DIM), (body, COL_TEXT)]
        for s, col in parts:
            if not s:
                continue
            d.text((x + 1, y + 1), s, font=self.font, fill=sh)
            d.text((x, y), s, font=self.font, fill=col + (int(255 * a),))
            x += self.font.getlength(s)


def ease(u):
    u = max(0.0, min(1.0, u))
    return u * u * (3 - 2 * u)


def build(wallpaper, out_dir, w, h, preview=False, log_path=None, bar=(0.0, 1.0)):
    os.makedirs(out_dir, exist_ok=True)
    r = Renderer(wallpaper, w, h, log_path, bar)
    frames = {"part0": [], "part1": [], "part2": []}

    n0 = int(PART0_LEN * FPS)
    for f in range(n0):
        t = f / FPS
        bg = LK_DIM + (1 - LK_DIM) * ease(t / FADE_IN)
        frames["part0"].append((t, bg, ease((t - 0.3) / FADE_IN), None))
    t_end = PART0_LEN
    for f in range(CURSOR_FRAMES):
        frames["part1"].append((t_end, 1.0, 1.0, f < CURSOR_FRAMES // 2))
    n2 = int(FADE_OUT * FPS)
    for f in range(n2 + 1):
        a = 1 - ease(f / n2)
        frames["part2"].append((t_end, a, a, None))

    # static bootloader logo: same photo, dimmed, no text
    ImageEnhance.Brightness(r.bg).enhance(LK_DIM).save(os.path.join(out_dir, "lk_logo.png"))

    zpath = os.path.join(out_dir, "bootanimation.zip")
    previews = []
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_STORED) as z:
        desc = f"{w} {h} {FPS}\nc 1 0 part0 #000000\np 0 0 part1 #000000\nc 1 0 part2 #000000\n"
        z.writestr("desc.txt", desc)
        for part, lst in frames.items():
            for idx, (t, bg, ta, cur) in enumerate(lst):
                if preview and part == "part0" and idx % 5:
                    continue
                img = r.frame(t, bg, ta, cursor=cur)
                buf = io.BytesIO()
                img.save(buf, "JPEG", quality=JPEG_Q, optimize=True)
                z.writestr(f"{part}/{idx:05d}.jpg", buf.getvalue())
                if preview and (part != "part1" or idx in (0, CURSOR_FRAMES // 2)):
                    previews.append(img.resize((w // 2, h // 2), Image.LANCZOS))
    if preview:
        previews[0].save(os.path.join(out_dir, "preview.webp"), save_all=True,
                         append_images=previews[1:], duration=200, loop=0, quality=70)
    print(f"{w}x{h} lines={len(r.lines)} lines_visible={r.cap} line_h={r.lh} chars={r.max_chars} "
          f"frames={sum(len(v) for v in frames.values())} zip={os.path.getsize(zpath) / 1e6:.1f}MB")


if __name__ == "__main__":
    log = next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--log=")), None)
    bar = next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--bar=")), "0,1")
    bar = None if bar == "none" else tuple(map(float, bar.split(",")))
    build(sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), "--preview" in sys.argv, log, bar)
