# -*- coding: utf-8 -*-
"""抛物线 配图生成脚本。
单独运行：python m_parabola.py  （只生成这一张，写入 app/src/main/assets/img/m_parabola.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("抛物线", "到焦点与准线距离相等")
    px, py = coords(c, -2.6, 4.4, -3.0, 3.0)
    p = 1.2
    c.fn_curve(lambda v: math.sqrt(2 * p * v) if v >= 0 else None, 0, 4.2, px, py, color=BLUE)
    c.fn_curve(lambda v: -math.sqrt(2 * p * v) if v >= 0 else None, 0, 4.2, px, py, color=BLUE)
    c.dash(px(-p / 2), py(-3.0), px(-p / 2), py(3.0), 2, (170, 170, 170))
    c.circle(px(p / 2), py(0), 6, RED, RED, 2)
    c.t(px(p / 2) + 8, py(0) - 10, "F(\\frac{p}{2}, 0)", 17, True, RED, "lb")
    c.t(px(-p / 2) - 8, py(2.6), "准线 x = -\\frac{p}{2}", 16, False, GREY, "rt")
    c.rich(W // 2, H - 42, "y^{2} = 2px ：焦点 (\\frac{p}{2}, 0)，准线 x = -\\frac{p}{2}", 18)
    return c




if __name__ == "__main__":
    save(build(), "m_parabola")
