# -*- coding: utf-8 -*-
"""匀强电场与电场线 配图生成脚本。
单独运行：python p_efield.py  （只生成这一张，写入 app/src/main/assets/img/p_efield.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("匀强电场与电场线", "电场线越密场强越大，方向由正电荷指向负电荷")
    x1, y1, x2, y2 = 140, 160, 500, 340
    c.rect(x1, y1, x2, y1 + 18, (222, 232, 244), (44, 96, 178), 2, 3)
    c.rect(x1, y2 - 18, x2, y2, (248, 226, 226), (198, 58, 58), 2, 3)
    c.t(x2 + 12, y1 + 9, "+", 24, True, BLUE, "lm")
    c.t(x2 + 12, y2 - 9, "−", 24, True, RED, "lm")
    for i in range(6):
        x = x1 + 34 + i * 62
        c.arrow(x, y1 + 26, x, y2 - 26, 2, (110, 110, 110), 10)
    c.t((x1 + x2) / 2, y1 - 16, "极板间为匀强电场", 18, True, dark, "mb")
    c.rich(W // 2, H - 42, "E = \\frac{U}{d} ，F = qE ，电场线从正极板指向负极板", 17)
    return c




if __name__ == "__main__":
    save(build(), "p_efield")
