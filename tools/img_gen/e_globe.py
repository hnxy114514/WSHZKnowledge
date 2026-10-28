# -*- coding: utf-8 -*-
"""经纬网与地球仪 配图生成脚本。
单独运行：python e_globe.py  （只生成这一张，写入 app/src/main/assets/img/e_globe.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("经纬网与地球仪", "经线指示南北，纬线指示东西")
    ox, oy, R = 300, 250, 130
    c.circle(ox, oy, R, (238, 244, 250), (60, 90, 130), 3)
    for i in range(4):
        y = oy - R + 52 + i * 52
        hr = math.sqrt(max(1, R * R - (y - oy) ** 2))
        c.d.arc([ox - hr, y - 12, ox + hr, y + 12], 0, 180, fill=(60, 90, 130), width=2)
    for k in (-0.7, -0.35, 0, 0.35, 0.7):
        c.d.ellipse([ox - R * abs(k) if k else ox - 6, oy - R, ox + R * abs(k) if k else ox + 6, oy + R], outline=(60, 90, 130), width=2)
    c.d.line([ox, oy - R, ox, oy + R], fill=(198, 58, 58), width=3)
    c.t(ox, oy - R - 14, "N 北极", 15, True, (60, 90, 130), "mb")
    c.t(ox, oy + R + 16, "S 南极", 15, True, (60, 90, 130), "mt")
    c.t(ox + 10, oy + 8, "本初子午线", 14, True, RED, "la")
    c.arrow(460, 150, ox + 60, oy - 60, 2, GREY, 10)
    c.t(468, 146, "经线", 15, False, GREY, "lm")
    c.rich(W // 2, H - 40, "经度 180° 跨度、纬度 90°；1° 纬度约 111 km", 15)
    return c




if __name__ == "__main__":
    save(build(), "地理_1.2_经纬网与地理坐标")
