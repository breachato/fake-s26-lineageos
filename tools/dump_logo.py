"""Dump every logo entry to PNG and build a labelled contact sheet."""
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from mtklogo import read_entries  # noqa: E402

SIZES = {720 * 1560 * 4: (720, 1560), 720 * 1600 * 4: (720, 1600)}


def guess(n):
    if n in SIZES:
        return SIZES[n]
    px = n // 4
    for w in (48, 29, 163, 1, 64, 36, 40):
        if px % w == 0:
            return w, px // w
    return 1, px


def main(src, out):
    os.makedirs(out, exist_ok=True)
    _, _, entries = read_entries(src)
    thumbs = []
    for i, e in enumerate(entries):
        w, h = guess(len(e))
        im = Image.frombytes("RGBA", (w, h), e, "raw", "BGRA")
        im.save(os.path.join(out, f"{i:02d}_{w}x{h}.png"))
        t = im.copy()
        t.thumbnail((90, 190))
        thumbs.append((i, w, h, t))
    cols = 16
    rows = (len(thumbs) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * 100, rows * 215), (60, 60, 60))
    d = ImageDraw.Draw(sheet)
    for k, (i, w, h, t) in enumerate(thumbs):
        x, y = (k % cols) * 100 + 5, (k // cols) * 215 + 5
        bg = Image.new("RGB", t.size, (255, 0, 255))
        bg.paste(t, mask=t.split()[3])
        sheet.paste(bg, (x, y))
        d.text((x, y + 192), f"{i} {w}x{h}", fill=(255, 255, 255))
    sheet.save(os.path.join(out, "_sheet.png"))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
