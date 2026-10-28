# -*- coding: utf-8 -*-
"""指数函数与对数函数 配图生成脚本。
单独运行：python m_exp.py  （只生成这一张，写入 app/src/main/assets/img/m_exp.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("指数函数与对数函数", "互为反函数，图象关于 y = x 对称")
    px, py = coords(c, -3.2, 3.6, -3.2, 4.4)
    c.fn_curve(lambda v: 2 ** v, -3.2, 2.1, px, py, color=RED)
    c.fn_curve(lambda v: 0.5 ** v, -2.0, 3.4, px, py, color=BLUE)
    c.dash(px(-3.2), py(-3.2), px(3.6), py(3.6), 2, (160, 160, 160))
    c.circle(px(1), py(1), 5, GREEN, GREEN, 2)
    c.t(px(1) + 10, py(1) - 6, "(0,1)", 17, False, GREEN, "lb")
    c.t(px(2.0) + 16, py(2 ** 2.0) - 44, "y = 2^{x}", 18, True, RED, "ma")
    c.t(px(2.6), py(0.5 ** 2.6) + 34, "y = (\\frac{1}{2})^{x}", 18, True, BLUE, "ma")
    c.t(px(3.2), py(2.2), "y = x", 17, False, GREY, "lb")
    c.rich(W // 2, H - 42, "a>1 时 2^{x} 递增；(\\frac{1}{2})^{x} 递减；都过 (0,1)，值域恒正", 18)
    return c




if __name__ == "__main__":
    save(build(), "m_exp")
