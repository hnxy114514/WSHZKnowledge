# -*- coding: utf-8 -*-
"""共点力平衡 配图生成脚本。
单独运行：python p_equilibrium.py  （只生成这一张，写入 app/src/main/assets/img/p_equilibrium.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("共点力平衡", "三力平衡：任两力的合力与第三个力等大反向")
    ox, oy = 210, 210
    c.circle(ox, oy, 7, dark, dark, 1)
    c.t(ox + 12, oy + 14, "O", 18, True, dark, "la")
    # 三个力
    c.arrow(ox, oy, ox - 96, oy - 74, 4, RED, 14)     # 左上
    c.arrow(ox, oy, ox + 104, oy - 68, 4, BLUE, 14)   # 右上
    c.arrow(ox, oy, ox, oy + 108, 4, GREEN, 14)       # 向下
    c.t(ox - 100, oy - 84, "F_{1}", 20, True, RED, "rb")
    c.t(ox + 112, oy - 74, "F_{2}", 20, True, BLUE, "lb")
    c.t(ox + 10, oy + 104, "F_{3}", 20, True, GREEN, "la")
    c.t(ox, oy - 6, "合力 = 0", 16, True, GREY, "mb")
    # 右侧矢量三角形：首尾相接闭合
    tx, ty = 430, 250
    p1 = (tx - 90, ty + 60)
    p2 = (tx + 100, ty + 44)
    p3 = (tx + 6, ty - 96)
    c.arrow(p1[0], p1[1], p2[0], p2[1], 4, RED, 14)
    c.arrow(p2[0], p2[1], p3[0], p3[1], 4, BLUE, 14)
    c.arrow(p3[0], p3[1], p1[0], p1[1], 4, GREEN, 14)
    c.t(tx - 2, ty + 50, "F_{1}", 18, True, RED, "ma")
    c.t(tx + 74, ty - 20, "F_{2}", 18, True, BLUE, "lm")
    c.t(tx - 46, ty - 18, "F_{3}", 18, True, GREEN, "rm")
    c.t(tx, ty + 112, "首尾相接 = 闭合三角形", 15, True, GREY, "ma")
    c.rich(W // 2, H - 42, "平衡条件：∑F = 0；正交分解后 ∑F_{x}=0 、∑F_{y}=0", 17)
    return c




if __name__ == "__main__":
    save(build(), "p_equilibrium")
