"""Draws the wallpapers of addons/dynamic-wallpaper: 12 dark 1280x720 PNGs, one per weather mood and
time of day, from code, so the set is this repo's own work (no downloaded photo, no licence question) and
can be changed here.

    python3 addons/dynamic-wallpaper/tools/make_wallpapers.py <folder>

Bands, top to bottom, each a vertical gradient; a horizon glow where the scene wants one, a few stars for
night and rain streaks or a cloud deck for the wet moods. Every picture keeps its lower third near black
because the dock, the tile names and the header text sit there. Fixed seeds: the same code draws the same
pictures. Written as PNG at 1280x720 and 8-bit RGB, which is what the add-on's cache holds, about 30 KB each.
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw

W, H = 1280, 720


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def sky(top, bottom, horizon=None, glow=None, glow_y=0.62, glow_r=0.55):
    """The backdrop: a vertical gradient, an optional warm glow at the horizon and a floor darkening.

    The glow is a radial falloff computed per pixel, not a stack of ellipses: stacked ones left a visible
    edge where the last ring stopped, which is exactly the artefact a wallpaper must not show.
    """
    img = Image.new("RGB", (W, H), bottom)
    px = img.load()
    for y in range(H):
        # Eased: the top band changes slowly, the floor quickly, as a real sky does.
        t = (y / (H - 1)) ** 0.85
        row = lerp(top, bottom, t)
        for x in range(W):
            px[x, y] = row
    if glow:
        gx, gy, r = W * 0.5, H * glow_y, H * glow_r * 1.6
        for y in range(H):
            dy = (y - gy) / r
            for x in range(W):
                dx = (x - gx) / r
                d = math.hypot(dx, dy)
                if d >= 1:
                    continue
                a = glow[1] * (1 - d) ** 2.2
                if a <= 0.004:
                    continue
                px[x, y] = lerp(px[x, y], glow[0], a)
    return img, ImageDraw.Draw(img)


def vdarken(img, from_y, strength):
    """Darkens everything below from_y towards black, so text and tiles stay readable."""
    px = img.load()
    for y in range(int(H * from_y), H):
        t = max(0.0, (y - H * from_y) / (H - H * from_y))
        k = 1 - strength * (t ** 1.2)
        if k >= 0.999:
            continue
        for x in range(W):
            r, g, b = px[x, y]
            px[x, y] = (int(r * k), int(g * k), int(b * k))


def stars(draw, seed, n, ymax, colour=(210, 220, 255)):
    rnd = random.Random(seed)
    for _ in range(n):
        x, y = rnd.randrange(W), rnd.randrange(int(H * ymax))
        r = rnd.choice((1, 1, 1, 2))
        draw.ellipse([x, y, x + r, y + r], fill=colour)


def cloud_deck(img, seed, base_y, colour, layers=5):
    """Soft horizontal cloud bands: each a pile of wide ellipses, lightened in turn."""
    rnd = random.Random(seed)
    draw = ImageDraw.Draw(img, "RGBA")
    for i in range(layers):
        y = base_y + rnd.randrange(-40, 40)
        for _ in range(rnd.randrange(5, 9)):
            w = rnd.randrange(180, 460)
            h = rnd.randrange(28, 70)
            x = rnd.randrange(-120, W)
            a = rnd.randrange(18, 46)
            draw.ellipse([x, y, x + w, y + h], fill=colour + (a,))


def rain(img, seed, n, colour=(150, 170, 200), strength=(40, 90)):
    """Slanted streaks: rain without a texture file and without an animation frame."""
    rnd = random.Random(seed)
    draw = ImageDraw.Draw(img, "RGBA")
    for _ in range(n):
        x, y = rnd.randrange(W), rnd.randrange(int(H * 0.85))
        length = rnd.randrange(14, 38)
        draw.line([x, y, x + length * 0.35, y + length], fill=colour + (rnd.randrange(*strength),), width=1)


def ridge(draw, seed, base, amp, colour, rough=0.45):
    """A mountain silhouette: a zigzag with a smoothed random walk, filled to the bottom of the frame."""
    rnd = random.Random(seed)
    pts = [(0, H)]
    step = 26
    y = base
    for x in range(0, W + step, step):
        y += rnd.uniform(-amp * rough, amp * rough)
        y = min(max(y, base - amp), base + amp * 0.4)
        pts.append((x, int(y - amp * 0.5 * math.sin(x / 190.0 + seed))))
    pts.append((W, H))
    draw.polygon(pts, fill=colour)


def write(name, img, out):
    img.save(os.path.join(out, name + ".png"), optimize=True)


def clear_day(out):
    img, draw = sky((86, 132, 190), (168, 196, 220), glow=((255, 236, 200), 0.55), glow_y=0.58)
    cloud_deck(img, 11, int(H * 0.30), (236, 242, 250), layers=6)
    draw = ImageDraw.Draw(img)
    ridge(draw, 3, int(H * 0.68), 90, (54, 74, 96))
    ridge(draw, 7, int(H * 0.76), 60, (32, 46, 62))
    vdarken(img, 0.55, 0.72)
    write("clear_day", img, out)


def clear_night(out):
    img, draw = sky((8, 12, 28), (30, 42, 66))
    stars(draw, 5, 260, 0.62)
    draw.ellipse([W - 330, 70, W - 250, 150], fill=(232, 236, 224))
    ridge(draw, 3, int(H * 0.70), 90, (18, 24, 38))
    ridge(draw, 8, int(H * 0.78), 55, (10, 14, 22))
    vdarken(img, 0.55, 0.70)
    write("clear_night", img, out)


def clouds_day(out):
    img, draw = sky((112, 126, 146), (150, 162, 178))
    cloud_deck(img, 21, int(H * 0.28), (214, 220, 230), layers=8)
    cloud_deck(img, 22, int(H * 0.46), (176, 186, 200), layers=6)
    ridge(draw, 4, int(H * 0.72), 70, (58, 66, 78))
    ridge(draw, 9, int(H * 0.79), 45, (38, 44, 54))
    vdarken(img, 0.55, 0.74)
    write("clouds_day", img, out)


def clouds_night(out):
    img, draw = sky((16, 20, 34), (44, 50, 66))
    cloud_deck(img, 31, int(H * 0.34), (96, 104, 124), layers=7)
    ridge(draw, 4, int(H * 0.72), 70, (16, 20, 30))
    vdarken(img, 0.55, 0.72)
    write("clouds_night", img, out)


def rain_day(out):
    img, draw = sky((64, 74, 90), (104, 114, 130))
    cloud_deck(img, 41, int(H * 0.22), (120, 130, 148), layers=8)
    rain(img, 42, 900)
    ridge(draw, 5, int(H * 0.70), 80, (40, 46, 58))
    ridge(draw, 10, int(H * 0.78), 50, (26, 30, 40))
    vdarken(img, 0.55, 0.76)
    write("rain_day", img, out)


def rain_night(out):
    img, draw = sky((10, 14, 24), (34, 40, 54))
    cloud_deck(img, 51, int(H * 0.26), (70, 78, 96), layers=7)
    rain(img, 52, 700, colour=(120, 140, 175), strength=(25, 70))
    ridge(draw, 5, int(H * 0.70), 80, (14, 18, 26))
    vdarken(img, 0.55, 0.74)
    write("rain_night", img, out)


def storm_day(out):
    img, draw = sky((40, 40, 56), (74, 72, 92), glow=((210, 190, 140), 0.20), glow_y=0.66)
    cloud_deck(img, 61, int(H * 0.18), (58, 56, 76), layers=9)
    rain(img, 62, 1300, strength=(40, 110))
    ridge(draw, 6, int(H * 0.68), 100, (28, 28, 40))
    ridge(draw, 11, int(H * 0.77), 60, (18, 18, 26))
    vdarken(img, 0.55, 0.78)
    write("storm_day", img, out)


def storm_night(out):
    img, draw = sky((8, 8, 18), (30, 26, 42))
    cloud_deck(img, 71, int(H * 0.20), (46, 42, 60), layers=9)
    rain(img, 72, 900, strength=(30, 80))
    draw = ImageDraw.Draw(img)
    # One lightning bolt, drawn as a polygon: the only sharp line in the set, on purpose.
    bolt = [(int(W * 0.66), int(H * 0.18)), (int(W * 0.62), int(H * 0.40)), (int(W * 0.66), int(H * 0.40)),
            (int(W * 0.60), int(H * 0.58)), (int(W * 0.72), int(H * 0.34)), (int(W * 0.67), int(H * 0.34)),
            (int(W * 0.73), int(H * 0.18))]
    draw.polygon(bolt, fill=(226, 222, 250))
    ridge(draw, 6, int(H * 0.70), 95, (12, 12, 20))
    vdarken(img, 0.55, 0.76)
    write("storm_night", img, out)


def fog_day(out):
    img, draw = sky((150, 152, 150), (188, 188, 184))
    for i, y in enumerate((int(H * 0.42), int(H * 0.55), int(H * 0.66))):
        cloud_deck(img, 81 + i, y, (214, 214, 210), layers=5)
    draw = ImageDraw.Draw(img)
    ridge(draw, 7, int(H * 0.74), 55, (96, 98, 96))
    vdarken(img, 0.55, 0.80)
    write("fog_day", img, out)


def fog_night(out):
    img, draw = sky((22, 24, 28), (58, 60, 64))
    for i, y in enumerate((int(H * 0.45), int(H * 0.58), int(H * 0.68))):
        cloud_deck(img, 91 + i, y, (86, 88, 94), layers=5)
    ridge(draw, 7, int(H * 0.74), 55, (30, 31, 34))
    vdarken(img, 0.55, 0.78)
    write("fog_night", img, out)


def snow_day(out):
    img, draw = sky((128, 146, 168), (196, 206, 218), glow=((255, 250, 240), 0.35), glow_y=0.60)
    ridge(draw, 12, int(H * 0.64), 110, (188, 196, 208))
    ridge(draw, 13, int(H * 0.73), 70, (140, 150, 166))
    rnd = random.Random(99)
    for _ in range(500):
        x, y = rnd.randrange(W), rnd.randrange(int(H * 0.8))
        r = rnd.choice((1, 1, 2, 2, 3))
        draw.ellipse([x, y, x + r, y + r], fill=(240, 244, 250))
    vdarken(img, 0.55, 0.74)
    write("snow_day", img, out)


def snow_night(out):
    img, draw = sky((10, 16, 30), (34, 44, 62))
    ridge(draw, 12, int(H * 0.66), 100, (40, 50, 68))
    ridge(draw, 13, int(H * 0.75), 60, (24, 32, 46))
    rnd = random.Random(98)
    for _ in range(400):
        x, y = rnd.randrange(W), rnd.randrange(int(H * 0.8))
        r = rnd.choice((1, 1, 2, 2))
        draw.ellipse([x, y, x + r, y + r], fill=(196, 208, 232))
    vdarken(img, 0.55, 0.72)
    write("snow_night", img, out)


SCENES = [clear_day, clear_night, clouds_day, clouds_night, rain_day, rain_night,
          storm_day, storm_night, fog_day, fog_night, snow_day, snow_night]

if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1].startswith("-"):
        print("usage: make_wallpapers.py <folder>", file=sys.stderr)
        sys.exit(2)
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for scene in SCENES:
        scene(out)
    print(len(SCENES), "wallpapers written to", out)
