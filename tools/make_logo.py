"""Build a new MediaTek logo.bin: dimmed wallpaper as boot logo and an
iOS-style charging theme (pure black, thin grey battery, green / red fill).

The level screen keeps the stock geometry because lk paints the level as
163x29 stripes into a fixed vertical cavity and the digits at fixed spots;
screens without a level indicator use the horizontal iPhone battery.

Usage: python make_logo.py STOCK_LOGO WALLPAPER OUT_DIR
"""
import os
import sys

from PIL import Image, ImageDraw, ImageEnhance, ImageFont

sys.path.insert(0, os.path.dirname(__file__))
from make_bootanim import LK_DIM, Renderer  # noqa: E402
from mtklogo import read_entries, write_logo  # noqa: E402

W, H = 720, 1560
PART_SIZE = 8 * 1024 * 1024
# battery cavity measured from the stock images (inclusive pixel bounds)
CX0, CX1, CY0, CY1 = 278, 440, 696, 983
CAV_H = CY1 - CY0 + 1                  # 288
TEXT_Y = 526                           # top of the "100%" text in the stock image
SS = 4                                 # supersampling for shapes

BLACK = (0, 0, 0)
OUTLINE = (112, 112, 118)              # iOS battery outline grey
NUB = (82, 82, 88)
GREEN = (52, 199, 89)                  # iOS systemGreen
RED = (255, 59, 48)                    # iOS systemRed
WHITE = (242, 242, 247)

FONT = r"C:\Windows\Fonts\segoeui.ttf"


def supersampled(draw_fn):
    big = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(big), lambda *v: [round(c * SS) for c in v])
    return big.resize((W, H), Image.LANCZOS)


def over(img, layer):
    out = img.convert("RGBA")
    out.alpha_composite(layer)
    return out.convert("RGB")


def font_for(height, sample="0"):
    size = height * 2
    font = ImageFont.truetype(FONT, size)
    while font.getbbox(sample)[3] - font.getbbox(sample)[1] > height and size > 8:
        size -= 1
        font = ImageFont.truetype(FONT, size)
    return font


def centered_text(img, s, top, height, color=WHITE):
    font = font_for(height)
    d = ImageDraw.Draw(img)
    bb = d.textbbox((0, 0), s, font=font)
    ref = d.textbbox((0, 0), "0", font=font)
    d.text(((W - (bb[2] - bb[0])) // 2 - bb[0], top - ref[1]), s, font=font, fill=color)


# --- vertical battery (level screens) -----------------------------------------
def vertical_battery(fill_rows=0, color=GREEN):
    def shape(d, s):
        d.rounded_rectangle(s(262, 680, 456, 1000), radius=26 * SS, outline=OUTLINE + (255,), width=5 * SS)
        d.rounded_rectangle(s(326, 664, 392, 676), radius=6 * SS, fill=NUB + (255,))
    img = over(Image.new("RGB", (W, H), BLACK), supersampled(shape))
    ImageDraw.Draw(img).rectangle([CX0, CY0, CX1, CY1], fill=BLACK)
    if fill_rows:
        ImageDraw.Draw(img).rectangle([CX0, CY1 - fill_rows + 1, CX1, CY1], fill=color)
    return img


# --- horizontal iPhone battery (no level overlay) -----------------------------
def horizontal_battery(sliver=RED, cy=700, bolt=False, cable=False, bang=False):
    x0, x1, y0, y1 = 210, 490, cy - 64, cy + 64

    def shape(d, s):
        d.rounded_rectangle(s(x0, y0, x1, y1), radius=30 * SS, outline=OUTLINE + (255,), width=7 * SS)
        d.rounded_rectangle(s(x1 + 10, cy - 22, x1 + 22, cy + 22), radius=6 * SS, fill=NUB + (255,))
        if sliver:
            d.rounded_rectangle(s(x0 + 16, y0 + 16, x0 + 40, y1 - 16), radius=10 * SS, fill=sliver + (255,))
        if bolt:
            pts = [(12, -46), (-22, 6), (-2, 6), (-12, 46), (22, -8), (2, -8)]
            d.polygon([(round((360 + x * 1.1) * SS), round((cy + y * 1.1) * SS)) for x, y in pts],
                      fill=WHITE + (255,))
        if cable:
            # Lightning connector + cable pointing up at the battery, like the iOS dead-battery screen
            cx, top = 360, cy + 150
            d.rounded_rectangle(s(cx - 9, top, cx + 9, top + 46), radius=5 * SS, fill=(200, 200, 205, 255))
            d.rounded_rectangle(s(cx - 26, top + 40, cx + 26, top + 150), radius=14 * SS, fill=WHITE + (255,))
            d.rounded_rectangle(s(cx - 11, top + 148, cx + 11, top + 300), radius=8 * SS, fill=WHITE + (255,))
        if bang:
            d.rounded_rectangle(s(355, cy - 40, 365, cy + 14), radius=5 * SS, fill=RED + (255,))
            d.ellipse(s(354, cy + 26, 366, cy + 38), fill=RED + (255,))
    return over(Image.new("RGB", (W, H), BLACK), supersampled(shape))


def glyph(ch, w, h=64):
    im = Image.new("RGB", (w, h), BLACK)
    font = font_for(round(h * 0.78))
    d = ImageDraw.Draw(im)
    bb = d.textbbox((0, 0), ch, font=font)
    ref = d.textbbox((0, 0), "0", font=font)
    d.text(((w - (bb[2] - bb[0])) // 2 - bb[0], (h - (ref[3] - ref[1])) // 2 - ref[1]), ch, font=font, fill=WHITE)
    return im


def pulse_bar(k, frames=10, w=163, h=29):
    # the top stripe of the level gently "breathes" instead of a wave
    import math
    a = 0.5 - 0.5 * math.cos(2 * math.pi * k / frames)
    top = tuple(round(c + (255 - c) * 0.35 * a) for c in GREEN)
    im = Image.new("RGB", (w, h), GREEN)
    d = ImageDraw.Draw(im)
    for y in range(h):
        f = 1 - y / (h - 1)
        d.line([(0, y), (w - 1, y)], fill=tuple(round(GREEN[i] + (top[i] - GREEN[i]) * f) for i in range(3)))
    return im


def to_bgra(img):
    return img.convert("RGBA").tobytes("raw", "BGRA")


def build(stock, wallpaper, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    header, _, entries = read_entries(stock)
    assert len(entries) == 80
    lk = ImageEnhance.Brightness(Renderer(wallpaper, W, H).bg).enhance(LK_DIM)
    new = {0: lk, 38: lk}

    new[1] = vertical_battery(0)
    new[35] = vertical_battery(29)                       # level background
    full = vertical_battery(CAV_H)
    centered_text(full, "100%", TEXT_Y, 50)
    new[37] = full
    for i, rows in zip(range(25, 35), [23, 18, 11, 7, 5, 6, 8, 13, 19, 24]):
        new[i] = vertical_battery(rows, RED)                 # low-battery pulse
    new[2] = horizontal_battery()
    new[40] = horizontal_battery()
    new[39] = horizontal_battery(bolt=True)
    new[41] = horizontal_battery(cable=True)
    err = horizontal_battery(sliver=None, bang=True)
    centered_text(err, "Unplug the charger", 860, 26, (174, 174, 178))
    new[3] = err
    for dgt in range(10):
        new[4 + dgt] = glyph(str(dgt), 45)
    new[14] = glyph("%", 57)
    for k in range(10):
        new[15 + k] = pulse_bar(k)
    new[36] = Image.new("RGB", (163, 1), GREEN)
    for i in range(42, 80):
        new[i] = Image.new("RGB", (720, 1600))               # leftover HYUNDAI / MediaTek frames

    out_entries = []
    for i, old in enumerate(entries):
        b = to_bgra(new[i]) if i in new else old
        assert len(b) == len(old), (i, len(b), len(old))
        out_entries.append(b)
        if i in new:
            new[i].save(os.path.join(out_dir, f"{i:02d}.png"))
    size = write_logo(header, out_entries, os.path.join(out_dir, "logo_new.bin"), PART_SIZE)
    print("logo_new.bin", size, "bytes of", PART_SIZE)


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2], sys.argv[3])
