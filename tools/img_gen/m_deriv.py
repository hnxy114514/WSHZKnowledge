# -*- coding: utf-8 -*-
"""导数的几何意义 配图生成脚本。
单独运行：python m_deriv.py  （只生成这一张，写入 app/src/main/assets/img/m_deriv.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("导数的几何意义", "f′(x_{0}) 就是该点切线的斜率 k = tanα")
    px, py = coords(c, -1.0, 4.2, -1.2, 4.6)
    f = lambda v: 0.42 * v ** 3 - 1.2 * v + 0.6
    d = lambda v: 1.26 * v ** 2 - 1.2
    c.fn_curve(f, -0.6, 3.9, px, py, color=BLUE)
    x0 = 2.0
    k = d(x0)
    y0 = f(x0)
    c.line(px(0.8), py(y0 + k * (0.8 - x0)), px(3.2), py(y0 + k * (3.2 - x0)), 3, RED)
    c.circle(px(x0), py(y0), 6, RED, RED, 2)
    c.t(px(x0) + 10, py(y0) - 12, "切点 A(x_{0}, f(x_{0}))", 17, True, RED, "lb")
    c.t(px(1.0), py(3.9), "k = f′(x_{0}) = tanα", 19, True, (30, 30, 30), "la")
    c.rich(W // 2, H - 42, "切线方程：y − f(x_{0}) = f′(x_{0})(x − x_{0})", 18)
    return c




if __name__ == "__main__":
    save(build(), "m_deriv")
