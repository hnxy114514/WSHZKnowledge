# -*- coding: utf-8 -*-
"""平抛运动 配图生成脚本。
单独运行：python p_projectile.py  （只生成这一张，写入 app/src/main/assets/img/p_projectile.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("平抛运动", "水平匀速 + 竖直自由落体，轨迹是抛物线")
    ox, oy = 110, 150
    c.arrow(ox - 24, oy, ox + 470, oy, 2, (80, 80, 80), 11)
    c.arrow(ox, oy - 26, ox, oy + 236, 2, (80, 80, 80), 11)
    c.t(ox + 466, oy - 8, "x", 17, False, GREY, "mb")
    c.t(ox - 8, oy + 232, "y", 17, False, GREY, "rt")
    gx, gy = 1.6, 0.42
    c.fn_curve(lambda v: -gy * v * v, 0, 15.6, lambda v: ox + v * 28, lambda y: oy - y * 60, 200, RED, 4)
    vx, vy = ox + 14 * 28, oy - (-gy * 14 * 14) * 60
    c.circle(vx, vy, 6, RED, RED, 2)
    c.arrow(vx, vy, vx + 62, vy, 3, BLUE, 12)
    c.t(vx + 66, vy, "v_{x} = v_{0}", 17, True, BLUE, "lm")
    c.arrow(vx, vy, vx, vy + 52, 3, GREEN, 12)
    c.t(vx + 8, vy + 46, "v_{y} = gt", 17, True, GREEN, "la")
    c.arrow(vx, vy, vx + 58, vy + 44, 3, (120, 60, 160), 12)
    c.t(vx + 26, vy + 70, "v", 18, True, (120, 60, 160), "la")
    c.t(ox + 6, oy - 14, "O", 16, False, GREY, "lb")
    c.rich(W // 2, H - 42, "x = v_{0}t ，y = \\frac{gt^{2}}{2} ，落地时间只由高度决定", 17)
    return c




if __name__ == "__main__":
    save(build(), "物理_4.1_平抛")
