# -*- coding: utf-8 -*-
"""DNA 双螺旋与中心法则 配图生成脚本。
单独运行：python b_dna.py  （只生成这一张，写入 app/src/main/assets/img/b_dna.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("DNA 双螺旋与中心法则", "碱基互补配对 A—T、G—C")
    x0 = 150
    for i in range(10):
        y = 140 + i * 22
        c.line(x0, y, x0 + 40, y + 10, 3, BLUE)
        c.line(x0 + 70, y, x0 + 110, y + 10, 3, (200, 120, 60))
        c.line(x0 + 40, y + 10, x0 + 70, y, 2, (150, 150, 150))
    c.t(x0 + 20, 128, "一条链", 15, True, BLUE, "mb")
    c.t(x0 + 90, 128, "另一条链", 15, True, (200, 120, 60), "mb")
    c.t(x0 + 55, 366, "氢键", 15, False, GREY, "ma")
    # 中心法则
    c.rect(300, 176, 396, 214, "white", (60, 60, 60), 2, 6)
    c.t(348, 195, "DNA", 17, True, dark, "mm")
    c.rect(300, 268, 396, 306, "white", (60, 60, 60), 2, 6)
    c.t(348, 287, "RNA", 17, True, dark, "mm")
    c.rect(454, 268, 556, 306, "white", (60, 60, 60), 2, 6)
    c.t(505, 287, "蛋白质", 16, True, dark, "mm")
    c.arrow(348, 214, 348, 264, 2, RED, 10)
    c.t(356, 240, "转录", 14, True, RED, "lm")
    c.arrow(396, 287, 450, 287, 2, GREEN, 10)
    c.t(422, 272, "翻译", 14, True, GREEN, "mb")
    c.arrow(348, 176, 348, 132, 2, BLUE, 10)
    c.t(356, 150, "复制", 14, True, BLUE, "lm")
    c.rich(W // 2, H - 40, "DNA → RNA → 蛋白质；逆转录、RNA 复制见于某些病毒", 15)
    return c




if __name__ == "__main__":
    save(build(), "生物_5.3_中心法则")
