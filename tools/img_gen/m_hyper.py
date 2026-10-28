# -*- coding: utf-8 -*-
"""双曲线 配图生成脚本。
单独运行：python m_hyper.py  （只生成这一张，写入 app/src/main/assets/img/m_hyper.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("双曲线", "到两焦点距离之差的绝对值为 2a")
    px, py = coords(c, -4.4, 4.4, -3.2, 3.2)
    a, b = 1.5, 1.15
    c.fn_curve(lambda v: b * math.sqrt(v * v / (a * a) - 1) if abs(v) >= a else None, -4.2, 4.2, px, py, color=BLUE)
    c.fn_curve(lambda v: -b * math.sqrt(v * v / (a * a) - 1) if abs(v) >= a else None, -4.2, 4.2, px, py, color=BLUE)
    c.dash(px(-4.2), py(-b * 4.2 / a), px(4.2), py(b * 4.2 / a), 1, (170, 170, 170))
    c.dash(px(-4.2), py(b * 4.2 / a), px(4.2), py(-b * 4.2 / a), 1, (170, 170, 170))
    cc = math.sqrt(a * a + b * b)
    for sx in (-1, 1):
        c.circle(px(sx * cc), py(0), 6, RED, RED, 2)
    c.t(px(2.9), py(-2.9), "渐近线 y = ±\\frac{b}{a}x", 17, False, GREY, "ma")
    c.t(px(-1.1), py(2.7), "两支关于 y 轴对称", 17, False, BLUE, "ma")
    c.rich(W // 2, H - 42, "\\frac{x^{2}}{a^{2}} − \\frac{y^{2}}{b^{2}} = 1 ，c^{2} = a^{2} + b^{2} ，e = \\frac{c}{a} > 1", 18)
    return c




if __name__ == "__main__":
    save(build(), "数学_9.4_定义")
