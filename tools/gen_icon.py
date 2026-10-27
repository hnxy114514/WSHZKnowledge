# -*- coding: utf-8 -*-
"""「腕上知识汇总」App 图标：渐变圆底 + 打开的书 + 金色根号（呼应数学公式排版）。"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

BG_OUT = (14, 24, 48)      # 外圈深海蓝
BG_IN = (66, 44, 122)      # 中心靛紫
GOLD = (255, 202, 64)
PAGE = (250, 248, 241)
LINE = (176, 186, 206)
SPINE = (46, 56, 88)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def make_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2.0
    r = size / 2.0

    # 径向渐变圆底
    steps = int(r)
    for i in range(steps, 0, -1):
        rr = r * i / steps
        col = lerp(BG_IN, BG_OUT, (rr / r) ** 1.3)
        d.ellipse([c - rr, c - rr, c + rr, c + rr], fill=col + (255,))

    # 金色星光
    stars = [
        (0.28, 0.20, 0.026), (0.50, 0.13, 0.034), (0.72, 0.21, 0.024),
        (0.19, 0.34, 0.018), (0.81, 0.36, 0.020), (0.40, 0.28, 0.013),
    ]
    for sx, sy, sr in stars:
        px, py, pr = sx * size, sy * size, sr * size
        d.ellipse([px - pr, py - pr, px + pr, py + pr], fill=GOLD + (220,))

    # 打开的书
    bw = size * 0.54
    bh = size * 0.32
    top = size * 0.49
    bot = top + bh
    mid = top + bh * 0.13
    left = [(c - bw / 2, top), (c - bw * 0.05, mid), (c - bw * 0.05, bot), (c - bw / 2, bot)]
    right = [(c + bw / 2, top), (c + bw * 0.05, mid), (c + bw * 0.05, bot), (c + bw / 2, bot)]
    d.polygon(left, fill=PAGE + (255,))
    d.polygon(right, fill=PAGE + (255,))
    d.line([(c, mid), (c, bot)], fill=SPINE + (255,), width=max(1, int(size * 0.014)))

    # 金色根号（书页中央）
    lw = max(2, int(size * 0.036))
    hx1, hx2, hy = c - bw * 0.28, c + bw * 0.30, top + bh * 0.24
    d.line([(hx1, hy), (hx2, hy)], fill=GOLD + (255,), width=lw)
    d.line([(hx1, hy), (hx1 - bw * 0.15, hy + bh * 0.58), (hx1 + bw * 0.11, hy + bh * 0.34)],
           fill=GOLD + (255,), width=lw, joint="curve")

    # 书页文字线（根号下方）
    for k in range(2):
        yy = top + bh * (0.74 + k * 0.15)
        d.line([(c - bw * 0.40, yy), (c - bw * 0.08, yy)], fill=LINE + (190,), width=max(1, int(size * 0.010)))
        d.line([(c + bw * 0.08, yy), (c + bw * 0.40, yy)], fill=LINE + (190,), width=max(1, int(size * 0.010)))

    # 外圈柔光描边
    d.ellipse([1, 1, size - 2, size - 2], outline=GOLD + (70,), width=max(1, int(size * 0.015)))

    return img


def main():
    for folder, size in SIZES.items():
        out_dir = os.path.join(RES, folder)
        os.makedirs(out_dir, exist_ok=True)
        img = make_icon(size)
        path = os.path.join(out_dir, "ic_launcher.png")
        img.save(path, "PNG")
        print("生成:", path, size)
    print("图标全部生成完毕")


if __name__ == "__main__":
    main()
