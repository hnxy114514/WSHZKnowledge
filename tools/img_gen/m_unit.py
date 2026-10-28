# -*- coding: utf-8 -*-
"""单位圆与三角函数线 配图生成脚本。
单独运行：python m_unit.py  （只生成这一张，写入 app/src/main/assets/img/m_unit.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("单位圆与三角函数线", "终边与单位圆交点的坐标就是 (cosα, sinα)")
    ox, oy, R = 250, 258, 128
    c.circle(ox, oy, R, None, (70, 70, 70), 2)
    c.arrow(ox - R - 30, oy, ox + R + 40, oy, 2, (70, 70, 70), 11)
    c.arrow(ox, oy + R + 30, ox, oy - R - 30, 2, (70, 70, 70), 11)
    a = math.radians(38)
    ex, ey = ox + R * math.cos(a), oy - R * math.sin(a)
    c.line(ox, oy, ex, ey, 3, RED)
    c.circle(ex, ey, 6, RED, RED, 2)
    c.t(ex + 10, ey - 10, "P(cosα, sinα)", 17, True, RED, "lb")
    c.line(ex, ey, ex, oy, 2, BLUE)
    c.line(ex, ey, ox, ey, 2, GREEN)
    c.t(ex + 12, (ey + oy) / 2, "sinα", 17, True, BLUE, "lm")
    c.t((ex + ox) / 2, ey - 14, "cosα", 17, True, GREEN, "mb")
    c.t(ox + 62, oy - 26, "α", 19, True, (30, 30, 30), "lm")
    c.t(ox + 6, oy + R + 20, "1", 16, False, GREY, "la")
    c.rich(W // 2, H - 42, "sin^{2}α + cos^{2}α = 1 ，tanα = sinα / cosα", 19)
    return c




if __name__ == "__main__":
    save(build(), "数学_5.1_三角函数线")
