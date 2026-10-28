# -*- coding: utf-8 -*-
"""二次函数图象 配图生成脚本。
单独运行：python m_quad.py  （只生成这一张，写入 app/src/main/assets/img/m_quad.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("二次函数图象", "开口方向、对称轴与顶点决定一切")
    px, py = coords(c, -3.4, 3.6, -3.2, 4.6)
    f = lambda v: 0.55 * (v - 1) ** 2 - 2
    c.dash(px(1), py(-3.2), px(1), py(4.6), 2, (150, 150, 150))
    c.fn_curve(f, -3.2, 3.2, px, py, color=RED)
    c.circle(px(1), py(-2), 6, RED, RED, 2)
    c.t(px(1) + 14, py(-2) + 22, "顶点(1,−2)", 19, True, RED, "la")
    c.t(px(1) + 10, py(4.2), "x=1 对称轴", 18, False, GREY, "la")
    c.t(px(-3.2), py(3.6), "a>0 开口向上", 19, True, BLUE, "la")
    c.rich(W // 2, H - 42, "y = ax^{2} + bx + c ，对称轴 x = \\frac{-b}{2a}，顶点 (\\frac{-b}{2a} , \\frac{4ac−b^{2}}{4a})", 18)
    return c




if __name__ == "__main__":
    save(build(), "数学_3.1_二次函数")
