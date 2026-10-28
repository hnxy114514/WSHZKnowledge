# -*- coding: utf-8 -*-
"""匀变速直线运动 v-t 图象 配图生成脚本。
单独运行：python p_vt.py  （只生成这一张，写入 app/src/main/assets/img/p_vt.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("匀变速直线运动 v-t 图象", "斜率是加速度，图象面积是位移")
    px, py = coords(c, -0.4, 6.4, -0.6, 7.2, (78, 120, 596, 372), "t", "v")
    c.fn_curve(lambda v: 1.2 + 0.9 * v, 0, 6.0, px, py, color=RED)
    c.poly([(px(0), py(0)), (px(4.0), py(0)), (px(4.0), py(1.2 + 3.6))], (222, 232, 244), (44, 96, 178), 2)
    c.dash(px(4.0), py(0), px(4.0), py(4.8), 1, (150, 150, 150))
    c.t(px(2.3), py(0.9), "面积 = 位移", 17, True, BLUE, "ma")
    c.t(px(4.9), py(4.4), "斜率 = 加速度 a", 17, True, RED, "lm")
    c.t(px(1.6), py(6.6), "v = v_{0} + at", 18, True, dark, "ma")
    c.rich(W // 2, H - 42, "v = v_{0} + at ，x = v_{0}t + \\frac{at^{2}}{2} ，v^{2} − v_{0}^{2} = 2ax", 17)
    return c




if __name__ == "__main__":
    save(build(), "p_vt")
