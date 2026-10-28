# -*- coding: utf-8 -*-
"""向量的加法 配图生成脚本。
单独运行：python m_vector.py  （只生成这一张，写入 app/src/main/assets/img/m_vector.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("向量的加法", "三角形法则与平行四边形法则")
    a = (110, -95)
    b = (75, 55)
    # 三角形法则
    ox, oy = 90, 290
    ax, ay = ox + a[0], oy + a[1]
    bx, by = ax + b[0], ay + b[1]
    c.arrow(ox, oy, ax, ay, 4, RED, 14)
    c.arrow(ax, ay, bx, by, 4, BLUE, 14)
    c.arrow(ox, oy, bx, by, 4, GREEN, 14)
    c.t(ox + 50, oy - 44, "a", 19, True, RED, "mm")
    c.t(ax + 44, ay + 26, "b", 19, True, BLUE, "lm")
    c.t(ox + 96, oy - 34, "a + b", 18, True, GREEN, "ma")
    c.t(ox + 92, oy + 40, "三角形法则", 14, True, GREY, "ma")
    # 平行四边形法则
    ox2, oy2 = 430, 290
    ax2, ay2 = ox2 + a[0], oy2 + a[1]
    bx2, by2 = ox2 + b[0], oy2 + b[1]
    c.dash(ax2, ay2, ax2 + b[0], ay2 + b[1], 2, (150, 150, 150))
    c.dash(bx2, by2, bx2 + a[0], by2 + a[1], 2, (150, 150, 150))
    c.arrow(ox2, oy2, ax2, ay2, 4, RED, 14)
    c.arrow(ox2, oy2, bx2, by2, 4, BLUE, 14)
    c.arrow(ox2, oy2, ax2 + b[0], ay2 + b[1], 4, GREEN, 14)
    c.t(ox2 + 52, oy2 - 44, "a", 18, True, RED, "mm")
    c.t(ox2 + 46, oy2 + 28, "b", 18, True, BLUE, "lm")
    c.t(ox2 + 100, oy2 - 34, "a+b", 18, True, GREEN, "ma")
    c.t(ox2 + 92, oy2 + 40, "平行四边形法则", 14, True, GREY, "ma")
    c.rich(W // 2, H - 42, "a + b 由平行四边形对角线或三角形首尾相接得到", 16)
    return c




if __name__ == "__main__":
    save(build(), "数学_6.1_线性运算")
