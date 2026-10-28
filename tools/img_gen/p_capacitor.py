# -*- coding: utf-8 -*-
"""平行板电容器 配图生成脚本。
单独运行：python p_capacitor.py  （只生成这一张，写入 app/src/main/assets/img/p_capacitor.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("平行板电容器", "板间是匀强电场，电容 C = \\frac{εS}{4πkd}")
    y1, y2 = 150, 250
    c.rect(120, y1 - 8, 500, y1 + 8, "white", (40, 40, 40), 3)
    c.rect(120, y2 - 8, 500, y2 + 8, "white", (40, 40, 40), 3)
    for i in range(5):
        x = 150 + i * 80
        c.arrow(x, y1 + 8, x, y2 - 8, 2, (90, 90, 90), 10)
    for i in range(6):
        x = 130 + i * 76
        c.t(x, y1 - 18, "+", 18, True, RED, "mm")
        c.t(x, y2 + 16, "−", 20, True, BLUE, "mm")
    c.t(560, y1, "A 板", 17, True, RED, "la")
    c.t(560, y2, "B 板", 17, True, BLUE, "la")
    c.t(310, (y1 + y2) // 2, "E 匀强", 17, True, (90, 90, 90), "mm")
    c.rich(W // 2, H - 42, "C = \\frac{εS}{4πkd}；U = Ed；带电后储存电能 W = \\frac{CU^{2}}{2}", 17)
    return c




if __name__ == "__main__":
    save(build(), "物理_7.2_电容器")
