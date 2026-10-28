# -*- coding: utf-8 -*-
"""洛伦兹力与圆周运动 配图生成脚本。
单独运行：python p_lorentz.py  （只生成这一张，写入 app/src/main/assets/img/p_lorentz.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("洛伦兹力与圆周运动", "v ⊥ B 时粒子做匀速圆周运动")
    x1, y1, x2, y2 = 90, 130, 560, 372
    c.rect(x1, y1, x2, y2, (244, 248, 252), (150, 160, 175), 2, 3)
    for i in range(6):
        for j in range(4):
            x, y = x1 + 40 + i * 85, y1 + 32 + j * 62
            c.t(x, y, "×", 20, True, (120, 130, 145), "mm")
    c.t(x1 + 14, y1 + 14, "B 垂直纸面向里", 16, True, GREY, "la")
    cx, cy, R = 350, 258, 84
    c.circle(cx, cy, R, None, (70, 70, 70), 2)
    mx, my = cx - R, cy
    c.circle(mx, my, 9, RED, RED, 1)
    c.arrow(mx, my, mx, my - 66, 3, BLUE, 12)
    c.t(mx - 4, my - 70, "v", 18, True, BLUE, "mb")
    c.arrow(mx, my, cx - 8, my, 3, GREEN, 12)
    c.t(cx - 46, my - 20, "F", 18, True, GREEN, "ma")
    c.rich(W // 2, H - 40, "r = mv/(qB) ，T = 2πm/(qB)：周期与速度无关", 17)
    return c




if __name__ == "__main__":
    save(build(), "p_lorentz")
