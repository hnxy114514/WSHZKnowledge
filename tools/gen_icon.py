# -*- coding: utf-8 -*-
"""程序化生成「汇总知识」App 图标（免费、无 AI、可精确适配圆屏）
设计：深蓝→紫径向渐变圆底 + 中央打开的书 + 上方金色星光，体现"汇总知识/学习"
"""
import os, math
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

BG_OUT = (11, 22, 46)      # 外圈深蓝
BG_IN = (59, 30, 110)      # 中心紫
GOLD = (255, 213, 79)
PAGE = (245, 247, 250)
LINE = (176, 190, 197)
SPINE = (40, 52, 90)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def make_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2.0
    r = size / 2.0

    # 1. 径向渐变圆底（同心圆插值）
    steps = int(r)
    for i in range(steps, 0, -1):
        rr = r * i / steps
        col = lerp(BG_IN, BG_OUT, (rr / r) ** 1.2)
        d.ellipse([c - rr, c - rr, c + rr, c + rr], fill=col + (255,))

    # 2. 金色星光（上方散布）
    stars = [
        (0.30, 0.22, 0.030), (0.50, 0.15, 0.038), (0.70, 0.24, 0.028),
        (0.20, 0.36, 0.020), (0.80, 0.38, 0.022), (0.38, 0.32, 0.016),
    ]
    for sx, sy, sr in stars:
        px, py, pr = sx * size, sy * size, sr * size
        d.ellipse([px - pr, py - pr, px + pr, py + pr], fill=GOLD + (230,))

    # 3. 打开的书（中心偏下）
    bw = size * 0.50          # 书总宽
    bh = size * 0.30          # 书高
    cx = c
    top = size * 0.52
    bot = top + bh
    mid = top + bh * 0.16     # 书页顶部中间凹陷（打开效果）

    left = [(cx - bw / 2, top), (cx - bw * 0.04, mid), (cx - bw * 0.04, bot), (cx - bw / 2, bot)]
    right = [(cx + bw / 2, top), (cx + bw * 0.04, mid), (cx + bw * 0.04, bot), (cx + bw / 2, bot)]
    d.polygon(left, fill=PAGE + (255,))
    d.polygon(right, fill=PAGE + (255,))

    # 书脊
    d.line([(cx, mid), (cx, bot)], fill=SPINE + (255,), width=max(1, int(size * 0.012)))

    # 书页横线（文字行）
    lw = max(1, int(size * 0.010))
    for k in range(3):
        yy = bot - bh * (0.22 + k * 0.20)
        d.line([(cx - bw * 0.42, yy), (cx - bw * 0.10, yy)], fill=LINE + (220,), width=lw)
        d.line([(cx + bw * 0.10, yy), (cx + bw * 0.42, yy)], fill=LINE + (220,), width=lw)

    # 4. 外圈柔光描边（高级感）
    d.ellipse([1, 1, size - 2, size - 2], outline=GOLD + (60,), width=max(1, int(size * 0.014)))

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
