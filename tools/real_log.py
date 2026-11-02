"""Pick readable lines from this phone's real boot log (dmesg + logcat) for the
boot animation. Writes one "[ uptime] message" line per row to OUT.

Usage: python real_log.py DMESG LOGCAT OUT
       python real_log.py --split DMESG LOGCAT OUT_PRE_PIN OUT_POST_PIN [DMESG_LATE]
"""
import re
import sys
from datetime import datetime

PRIVATE = re.compile(
    r"ssid|bssid|iphone|@|imei|iccid|msisdn|imsi|password|psk|account|"
    r"([0-9a-f]{2}[:-]){5}[0-9a-f]{2}|\+?\d{9,}", re.I)
NOISE = re.compile(
    r"avc:|audit|cpufreq|Offset|BATON|charger_hv|hps_main|CMDQ|Auxadc|AUXADC|cfsucc|"
    r"array_size|ETA6947_CONfig|\[name:|thermal|mtk_gpufreq|healthd|lowmemorykiller|"
    r"binder:|wake|Wakeup|PM: |suspend|resume|\\x0|Unable to write|took \d+ms and failed|"
    r"failed: symlink|tracing/trace_marker|kworker|ccci|md_|emdlogger|SPM|spm|SODI|"
    r"\(name:|mt_gpio|pinctrl|regulator|error|fail|not support|duplicate|warn|"
    r"^\s*at |\.java:|\]\[0x|Reg\[|val=0x|dump_register|={3,}|\*{3,}|get_lcm_info_fun|"
    r"migrate|Pre-boot|am_pre_boot|uc_send|Async|HeapDump|Mendel|Dialer|Vvm|ProtoData|"
    r"InputMethodEntry|ONSAutoboot|NavBarInflater|cpu_ntf|memblock|PHY layout|CONNADP", re.I)

KERNEL_KEEP = re.compile(
    r"Linux version|Machine model|Kernel command line|Memory:|CPU\d*: Booted|Brought up|"
    r"SMP: Total|psci|Reserved memory|ion|devtmpfs|NET: Registered|mmc0|mmcblk0|EXT4-fs|"
    r"mali|GPU|DISP|lcm|LCM|panel|touch|tpd|TP |cap_touch|camera|sensor|imgsensor|"
    r"gsensor|msensor|alsps|accel|gyro|battery|charger|fuel|ETA69|wusb|typec|"
    r"mt-snd-card|snd|wmt|WMT|wifi|wlan|bluetooth|BT |gps|consys|CONSYS|zram|"
    r"Freeing unused|Run /init|init: init|init: Loading|selinux|SELinux: Loaded|"
    r"init: Starting service|init: processing action \((early-init|init|late-init|fs|"
    r"post-fs|post-fs-data|boot|zygote-start)\)|init: Switching root|first_stage", re.I)

LOGCAT_KEEP = re.compile(
    r"Zygote|zygote|SystemServer|SystemServiceManager: Starting|ActivityManager: System now ready|"
    r"PackageManager: Scanning|WindowManager|InputManager|SurfaceFlinger: SurfaceFlinger is starting|"
    r"BootAnimation: BootAnimationStartTiming|vold: Vold|Cryptfs|cryptfs|"
    r"SystemUIService|ActivityTaskManager: START|Boot completed|boot_completed|"
    r"AudioFlinger|CameraService|lineage|am_proc_start: .*CryptKeeper", re.I)

PREFIX = re.compile(r"^\[\s*(\d+\.\d+)\]\s*[-(]*\(?\d*\)?\[\d+:[^\]]*\]\s*")
LOGCAT = re.compile(r"^(\d\d-\d\d \d\d:\d\d:\d\d\.\d+)(?:\s+[+-]\d{4})?\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]+?)\s*:\s(.*)$")
ANIM_SHOWN = re.compile(r"BootAnimation(?:Shown|Start)Timing.*?(\d+)ms")


def clean(msg):
    msg = re.sub(r"\s+", " ", msg.replace("\\x0a", "")).strip()
    msg = re.sub(r"androidboot\.serialno=\S+", "androidboot.serialno=<redacted>", msg)
    return msg


def kernel(path):
    out, seen = [], set()
    for raw in open(path, encoding="utf-8", errors="ignore"):
        raw = raw.strip()
        m = re.match(r"^\[\s*(\d+\.\d+)\]", raw)
        if not m:
            continue
        ts = float(m.group(1))
        msg = clean(PREFIX.sub("", raw))
        if not msg or NOISE.search(msg) or PRIVATE.search(msg) or not KERNEL_KEEP.search(msg):
            continue
        key = re.sub(r"\d+", "#", msg)[:60]
        if key in seen:
            continue
        seen.add(key)
        out.append((ts, msg))
    return out


def logcat(path, uptime_ref):
    out, seen = [], set()
    year = datetime.now().year
    for raw in open(path, encoding="utf-8", errors="ignore"):
        m = LOGCAT.match(raw.strip())
        if not m:
            continue
        stamp, _lvl, tag, msg = m.groups()
        line = f"{tag}: {clean(msg)}"
        if NOISE.search(line) or PRIVATE.search(line) or not LOGCAT_KEEP.search(line):
            continue
        t = datetime.strptime(f"{year}-{stamp}", "%Y-%m-%d %H:%M:%S.%f").timestamp()
        key = re.sub(r"\d+", "#", line)[:60]
        if key in seen:
            continue
        seen.add(key)
        out.append((t, line))
    # anchor wall-clock to uptime with the bootanimation start marker
    anchor = next(((t, l) for t, l in out if "BootAnimationStartTiming" in l), None)
    if anchor:
        ms = int(re.search(r"(\d+)ms", anchor[1]).group(1))
        off = ms / 1000 - anchor[0]
    else:
        off = uptime_ref - out[0][0] if out else 0
    return [(t + off, l) for t, l in out]


def dedup(rows):
    out, seen = [], set()
    for ts, msg in rows:
        key = re.sub(r"\d+", "#", msg)[:60]
        if key not in seen:
            seen.add(key)
            out.append((ts, msg))
    return out


def logcat_uptime(path):
    """All kept logcat lines on the uptime clock, plus the uptimes the boot animation started.

    Not deduplicated: on an encrypted device the same services start once before the
    PIN and again after it, and each half of the animation needs its own copy.
    """
    year = datetime.now().year
    rows, shown, off = [], [], None
    for raw in open(path, encoding="utf-8", errors="ignore"):
        m = LOGCAT.match(raw.strip())
        if not m:
            continue
        stamp, _lvl, tag, msg = m.groups()
        t = datetime.strptime(f"{year}-{stamp}", "%Y-%m-%d %H:%M:%S.%f").timestamp()
        a = ANIM_SHOWN.search(msg)
        if a:
            # wall clock -> uptime, from the animation's own start stamp
            off = int(a.group(1)) / 1000 - t
            shown.append(t)
        line = f"{tag}: {clean(msg)}"
        if NOISE.search(line) or PRIVATE.search(line) or not LOGCAT_KEEP.search(line):
            continue
        rows.append((t, line))
    if off is None:
        raise SystemExit("no BootAnimation timing line to anchor logcat")
    return [(t + off, l) for t, l in rows], [t + off for t in shown]


def write(rows, dst):
    with open(dst, "w", encoding="utf-8") as f:
        for ts, msg in rows:
            f.write(f"[{ts:12.6f}] {msg}\n")


def main_split(dmesg_boot, lc, dst1, dst2, dmesg_late=None):
    """Two logs for an encrypted boot: up to the PIN screen, and from the PIN to boot complete."""
    a, shown = logcat_uptime(lc)
    if len(shown) < 2:
        raise SystemExit("logcat holds only one boot animation; need the post-PIN one too")
    pin = next(t for t, m in a if "CryptKeeper" in m)
    p1 = sorted(kernel(dmesg_boot) + [r for r in a if r[0] <= pin], key=lambda r: r[0])
    p1 = dedup(p1)
    restart = shown[1] - 8.0          # framework restart right after the PIN is accepted
    done = next(t for t, m in a if t > restart and "Finished processing BOOT_COMPLETED" in m)
    late = [r for r in kernel(dmesg_late) if restart <= r[0] <= done] if dmesg_late else []
    p2 = dedup(sorted(late + [r for r in a if restart <= r[0] <= done], key=lambda r: r[0]))
    write(p1, dst1)
    write(p2, dst2)
    print(f"phase1={len(p1)} (until {pin:.1f}s)  phase2={len(p2)} ({restart:.1f}s..{done:.1f}s)")


def main(dmesg, lc, dst):
    k = kernel(dmesg)
    a = logcat(lc, k[-1][0] if k else 0)
    rows = sorted(k + [r for r in a if r[0] > 0], key=lambda r: r[0])
    end = next((i for i, (_, m) in enumerate(rows) if "Finished processing BOOT_COMPLETED" in m), None)
    if end is not None:
        rows = rows[:end + 1]
    with open(dst, "w", encoding="utf-8") as f:
        for ts, msg in rows:
            f.write(f"[{ts:12.6f}] {msg}\n")
    print(f"kernel={len(k)} android={len(a)} total={len(rows)}")


if __name__ == "__main__":
    if sys.argv[1] == "--split":
        main_split(*sys.argv[2:7])
    else:
        main(*sys.argv[1:4])
