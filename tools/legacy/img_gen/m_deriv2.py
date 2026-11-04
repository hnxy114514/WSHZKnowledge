# -*- coding: utf-8 -*-
"""导数的几何意义 配图生成脚本。
单独运行：python m_deriv2.py  （只生成这一张，写入 app/src/main/assets/img/m_deriv2.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("导数的几何意义", "f′(x_{0}) 是曲线在 x_{0} 处切线的斜率")
    px, py = coords(c, -2.2, 4.4, -3.4, 5.2, (80, 122, 596, 360), "x", "y")
    def f(x):
        return 0.22 * x ** 2 + 0.4 * x - 0.5
    c.fn_curve(f, -2.0, 4.0, px, py, 300, (44, 96, 178), 4)
    x0 = 1.8
    y0 = f(x0)
    k = 0.44 * x0 + 0.4
    # 切线
    def tan(x):
        return k * (x - x0) + y0
    c.fn_curve(tan, -1.2, 4.2, px, py, color=RED, w=3)
    c.circle(px(x0), py(y0), 6, RED, RED, 2)
    c.t(px(x0) + 12, py(y0) + 26, "切点 P(x_{0}, f(x_{0}))", 16, True, RED, "la")
    c.t(px(2.7), py(tan(2.7)) - 18, "切线斜率 = f′(x_{0})", 17, True, RED, "ma")
    c.t(px(-1.6), py(f(-1.6)) + 6, "y = f(x)", 17, True, BLUE, "lb")
    c.rich(W // 2, H - 42, "割线斜率 Δy/Δx，取极限得切线斜率 f′(x_{0}) = lim Δy/Δx", 16)
    return c




if __name__ == "__main__":
    save(build(), "数学_10.1_导数运算与切线")
