# -*- coding: utf-8 -*-
"""生成「腕上知识汇总」的应用图标。

思路：
  - 腕表：圆形深色盘面 + 双层金属表圈 + 12 点琥珀金三角标 + 3/6/9 点细刻度
  - 知识：盘面正中一本打开的书（Emoji，源图见 tools/emoji_src/）
  - 书底一层柔和投影，让图"浮"在盘面上；缩到小尺寸仍保持清晰剪影

图标里的书籍图形来自 OpenMoji（https://openmoji.org，CC BY-SA 4.0）。

用法：
  python tools/make_icon.py        # 写入 5 个 dpi 的 ic_launcher.png 并输出预览
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SS = 4                       # 超采样倍数
BASE = 192
S = BASE * SS
CX = CY = S / 2.0
R = S / 2.0 - 2.0

# 配色取自书本 Emoji 的封面蓝，保证表盘与图形是一套色
BG_IN = (26, 53, 84)        # 盘面中心：深蓝
BG_OUT = (6, 11, 18)        # 盘面边缘：近黑蓝
CASE = (45, 88, 128)        # 表壳外圈：暗蓝
BEZEL = (74, 162, 232)      # 表圈亮线：书封面的蓝
TICK = (156, 196, 230)      # 刻度：浅蓝
ACCENT = (143, 208, 245)    # 12 点标记：亮蓝

HERE = os.path.dirname(os.path.abspath(__file__))
EMOJI = os.path.join(HERE, "emoji_src", "open_book.png")


def radial_bg():
    yy, xx = np.mgrid[0:S, 0:S]
    d = np.clip(np.sqrt((xx - CX) ** 2 + (yy - CY) ** 2) / R, 0, 1)[..., None]
    a = np.array(BG_IN, float)
    b = np.array(BG_OUT, float)
    rgb = a * (1 - d) + b * d
    return Image.fromarray(
        np.dstack([rgb, np.full((S, S, 1), 255.0)]).astype("uint8"), "RGBA")


def draw_dial(img):
    """圆形表盘：外圈表壳 + 内圈亮线 + 四向刻度。"""
    d = ImageDraw.Draw(img)
    w1 = 0.030 * S
    d.ellipse([CX - R, CY - R, CX + R, CY + R], outline=CASE + (255,), width=int(w1))
    w2 = 0.009 * S
    r2 = R - w1 * 0.92
    d.ellipse([CX - r2 + w2, CY - r2 + w2, CX + r2 - w2, CY + r2 - w2],
              outline=BEZEL + (232,), width=int(w2))

    # 一圈 12 个小时刻度（3/6/9/12 更长更亮），表盘感而非十字准星
    r_far = R - w1 - 0.013 * S
    for i in range(12):
        if i == 0:
            continue
        rad = np.radians(i * 30.0)
        major = (i % 3 == 0)
        length = 0.034 * S if major else 0.020 * S
        width = int(0.010 * S) if major else int(0.0065 * S)
        col = TICK + (235,) if major else TICK + (140,)
        d.line([(CX + (r_far - length) * np.sin(rad), CY - (r_far - length) * np.cos(rad)),
                (CX + r_far * np.sin(rad), CY - r_far * np.cos(rad))],
               fill=col, width=width)

    # 12 点：亮蓝三角标
    rc = r_far
    half = 0.019 * S
    hgt = 0.032 * S
    d.polygon([(CX - half, CY - rc + hgt), (CX + half, CY - rc + hgt),
               (CX, CY - rc)], fill=ACCENT + (255,))


def draw_book(img, scale=0.505):
    """把 Emoji 书贴到盘面正中，并加一层柔和投影。"""
    em = Image.open(EMOJI).convert("RGBA")
    bbox = em.getbbox()
    if bbox:
        em = em.crop(bbox)
    w, h = em.size
    target = int(S * scale)
    k = target / max(w, h)
    em = em.resize((max(1, int(w * k)), max(1, int(h * k))), Image.LANCZOS)
    ew, eh = em.size
    x = int(CX - ew / 2)
    y = int(CY - eh / 2) + int(0.006 * S)

    sh = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse(
        [CX - ew * 0.52, y + eh * 0.62, CX + ew * 0.52, y + eh * 1.00],
        fill=(0, 0, 0, 165))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(0.018 * S)))
    img.alpha_composite(em, (x, y))


def build():
    img = radial_bg()
    draw_dial(img)
    draw_book(img)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).ellipse([CX - R, CY - R, CX + R, CY + R], fill=255)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def build_badge():
    """完整表盘（不裁圆），用于合成自适应图标的前景与兜底图。"""
    img = radial_bg()
    draw_dial(img)
    draw_book(img)
    return img


def compose_square(px, dial_ratio=1.0):
    """不透明方图：深色底 + 表盘，给 API 25 及以下的传统图标用（不露白边）。"""
    canvas = Image.new("RGBA", (px, px), BG_OUT + (255,))
    d = max(1, int(px * dial_ratio))
    badge = build_badge().resize((d, d), Image.LANCZOS)
    canvas.alpha_composite(badge, ((px - d) // 2, (px - d) // 2))
    return canvas


def compose_foreground(px, dial_ratio=0.88):
    """自适应图标前景：透明底 + 圆形表盘，直径占画布 2/3（正好等于系统可见区）。"""
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    d = max(1, int(px * dial_ratio))
    badge = build_badge().resize((d, d), Image.LANCZOS)
    mask = Image.new("L", (d, d), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, d - 1, d - 1], fill=255)
    badge.putalpha(mask)
    canvas.alpha_composite(badge, ((px - d) // 2, (px - d) // 2))
    return canvas


DPI_PX = (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192))
FG_PX = (("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432))

ADAPTIVE_XML = [
    '<?xml version="1.0" encoding="utf-8"?>',
    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">',
    '    <background android:drawable="@color/ic_launcher_background" />',
    '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />',
    '</adaptive-icon>',
    '',
]

COLORS_XML = [
    '<?xml version="1.0" encoding="utf-8"?>',
    '<resources>',
    '    <color name="ic_launcher_background">#060B12</color>',
    '</resources>',
    '',
]


def main():
    root = os.path.join(HERE, "..")
    res = os.path.join(root, "app", "src", "main", "res")

    # 1. 传统图标（API 25 及以下）：深色方底 + 满幅表盘，任何容器里都不会露白圈
    for dpi, px in DPI_PX:
        dst = os.path.join(res, "mipmap-" + dpi, "ic_launcher.png")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        compose_square(px * SS, 0.96).resize((px, px), Image.LANCZOS).save(dst, optimize=True)

    # 2. 自适应图标前景（API 26+）：表盘直径恰好等于可见区，视觉上铺满
    for dpi, px in FG_PX:
        dst = os.path.join(res, "mipmap-" + dpi, "ic_launcher_foreground.png")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        compose_foreground(px * 2, 0.88).resize((px, px), Image.LANCZOS).save(dst, optimize=True)

    # 3. 自适应图标描述文件与背景色
    anydpi = os.path.join(res, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    with open(os.path.join(anydpi, "ic_launcher.xml"), "w", encoding="utf-8") as f:
        f.write("\n".join(ADAPTIVE_XML))
    with open(os.path.join(res, "values", "colors.xml"), "w", encoding="utf-8") as f:
        f.write("\n".join(COLORS_XML))
    print("已写入 5 个传统图标 + 5 个前景图 + 自适应描述")

    # 4. 预览：模拟系统圆形遮罩后的样子
    fg = compose_foreground(432, 0.88)
    bg = Image.new("RGBA", (432, 432), (6, 11, 18, 255))
    merged = Image.alpha_composite(bg, fg)
    mask = Image.new("L", (432, 432), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, 431, 431], fill=255)
    circled = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    circled.paste(merged, (0, 0), mask)

    big = circled.resize((400, 400), Image.LANCZOS)
    sheet = Image.new("RGBA", (400 + 60 + 200, 420), (20, 21, 26, 255))
    sheet.paste(big, (10, 10), big)
    y = 40
    for px in (96, 64, 48, 32):
        im = circled.resize((px, px), Image.LANCZOS)
        sheet.paste(im, (430, y), im)
        y += px + 16
    dst = os.path.join(root, "build", "icon_preview.png")
    sheet.save(dst)
    print("预览:", dst)


if __name__ == "__main__":
    main()
