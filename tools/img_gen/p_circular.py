# -*- coding: utf-8 -*-
"""圆周运动与向心力 配图生成脚本。
单独运行：python p_circular.py  （只生成这一张，写入 app/src/main/assets/img/p_circular.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("圆周运动与向心力", "向心力指向圆心，由合外力提供")
    ox, oy, R = 320, 250, 128
    c.dash(ox - R - 20, oy, ox + R + 20, oy, 1, (170, 170, 170))
    c.circle(ox, oy, R, None, (70, 70, 70), 2)
    a = math.radians(-40)
    mx, my = ox + R * math.cos(a), oy + R * math.sin(a)
    c.circle(mx, my, 13, (250, 236, 226), (150, 90, 40), 3)
    c.arrow(mx, my, ox + 8, oy, 4, RED, 13)
    c.t(ox - 96, oy + 96, "F = \\frac{mv^{2}}{r}", 18, True, RED, "ma")
    c.arrow(ox - 60, oy + 78, ox - 10, oy + 26, 2, RED, 10)
    c.arrow(mx, my, mx + 58 * math.cos(a + 1.57), my + 58 * math.sin(a + 1.57), 3, BLUE, 12)
    c.t(mx + 34, my - 42, "v", 18, True, BLUE, "la")
    c.circle(ox, oy, 5, dark, dark, 1)
    c.t(ox + 14, oy + 12, "O", 17, False, GREY, "la")
    c.rich(W // 2, H - 42, "F = \\frac{mv^{2}}{r} = mω^{2}r ，ω = \\frac{2π}{T} ，v = \\frac{2πr}{T}", 17)
    return c




if __name__ == "__main__":
    save(build(), "p_circular")
