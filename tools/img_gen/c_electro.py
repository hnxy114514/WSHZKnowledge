# -*- coding: utf-8 -*-
"""电解饱和食盐水 配图生成脚本。
单独运行：python c_electro.py  （只生成这一张，写入 app/src/main/assets/img/c_electro.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("电解饱和食盐水", "阳极氯离子放电，阴极氢离子放电")
    c.rect(150, 170, 490, 340, (232, 242, 250), (120, 150, 180), 2, 4)
    c.line(230, 340, 230, 150, 4, RED)
    c.line(410, 340, 410, 150, 4, BLUE)
    c.t(230, 140, "阳极（石墨）", 16, True, RED, "mb")
    c.t(410, 140, "阴极（铁）", 16, True, BLUE, "mb")
    for i in range(5):
        c.circle(250 + i * 32, 220 + (i % 2) * 26, 5, None, (150, 170, 190), 1)
    c.t(196, 268, "Cl_{2}↑", 17, True, RED, "lm")
    c.t(424, 268, "H_{2}↑", 17, True, BLUE, "lm")
    c.t(320, 330, "NaOH 溶液", 16, False, (60, 90, 120), "mb")
    c.rich(W // 2, H - 40, "2NaCl + 2H_{2}O = 2NaOH + H_{2}↑ + Cl_{2}↑（电解）", 16)
    return c




if __name__ == "__main__":
    save(build(), "c_electro")
