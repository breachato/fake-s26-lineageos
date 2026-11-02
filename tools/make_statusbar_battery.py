"""Status-bar battery icons for the charge app: a horizontal battery with the percentage
cut out of it (bat_000..bat_100 + a level-list), plus a separate charging bolt.

Only alpha matters: SystemUI tints status-bar icons white or black to fit the background.
The empty part of the body is translucent and the charged part opaque; the digits are
cut out of the charged part and solid over the empty part.

Usage: python make_statusbar_battery.py ROBOTO_BOLD_TTF RES_DIR
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFont

SS = 8                      # supersampling
W, H = 58, 30               # xhdpi pixels = 29 x 15 dp (status_bar_system_icon_size is 15 dp)
BODY = (1, 3, 51, 27)       # x0, y0, x1, y1
BODY_R = 7
CAP = (53, 10.5, 56, 19.5)
EMPTY_ALPHA = 0.40
FONT_PX = 21


def s(v):
    return round(v * SS)


def body_mask():
    m = Image.new("L", (W * SS, H * SS), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([s(v) for v in BODY], radius=s(BODY_R), fill=255)
    return m


def battery(level, font):
    body = body_mask()
    # charged part: the body clipped at the level
    x0, _, x1, _ = BODY
    fill_x = x0 + (x1 - x0) * level / 100
    clip = Image.new("L", body.size, 0)
    ImageDraw.Draw(clip).rectangle([0, 0, s(fill_x), H * SS], fill=255)
    charged = ImageChops.multiply(body, clip)
    alpha = ImageChops.lighter(body.point(lambda v: v * EMPTY_ALPHA), charged)
    # terminal cap
    cap = Image.new("L", body.size, 0)
    ImageDraw.Draw(cap).rounded_rectangle([s(v) for v in CAP], radius=s(1.5),
                                          fill=round(255 * (1.0 if level == 100 else 0.5)))
    alpha = ImageChops.lighter(alpha, cap)
    # percentage cut out of the body
    text = Image.new("L", body.size, 0)
    td = ImageDraw.Draw(text)
    label = str(level)
    cx, cy = (BODY[0] + BODY[2]) / 2, (BODY[1] + BODY[3]) / 2
    td.text((s(cx), s(cy) + s(0.5)), label, font=font, fill=255, anchor="mm")
    # digits invert what is under them: cut out of the charged part, solid over the empty
    # part, so the number keeps its contrast at any level
    inverted = ImageChops.subtract(Image.new("L", body.size, 255), charged)
    alpha = Image.composite(inverted, alpha, text)
    img = Image.new("RGBA", body.size, (255, 255, 255, 0))
    img.putalpha(alpha)
    return img.resize((W, H), Image.LANCZOS)


def bolt():
    bw, bh = 18, 30
    m = Image.new("L", (bw * SS, bh * SS), 0)
    h = 22.0
    w = h * 0.56
    x, y = (bw - w) / 2, (bh - h) / 2
    pts = [(0.62, 0), (0.04, 0.58), (0.46, 0.58), (0.34, 1), (0.96, 0.40), (0.54, 0.40)]
    ImageDraw.Draw(m).polygon([(s(x + px * w), s(y + py * h)) for px, py in pts], fill=255)
    img = Image.new("RGBA", m.size, (255, 255, 255, 0))
    img.putalpha(m)
    return img.resize((bw, bh), Image.LANCZOS)


def main(ttf, res):
    out = os.path.join(res, "drawable-xhdpi")
    os.makedirs(out, exist_ok=True)
    os.makedirs(os.path.join(res, "drawable"), exist_ok=True)
    font = ImageFont.truetype(ttf, s(FONT_PX))
    items = []
    for lv in range(101):
        battery(lv, font).save(os.path.join(out, f"bat_{lv:03d}.png"), optimize=True)
        items.append(f'    <item android:maxLevel="{lv}" android:drawable="@drawable/bat_{lv:03d}" />')
    bolt().save(os.path.join(out, "sb_bolt.png"), optimize=True)
    with open(os.path.join(res, "drawable", "bat.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<level-list xmlns:android="http://schemas.android.com/apk/res/android">\n'
                + "\n".join(items) + "\n</level-list>\n")
    print(f"wrote 101 battery levels + bolt to {res}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
