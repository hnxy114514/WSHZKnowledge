# -*- coding: utf-8 -*-
"""等差数列与等比数列 配图生成脚本。
单独运行：python m_seq.py  （只生成这一张，写入 app/src/main/assets/img/m_seq.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("等差数列与等比数列", "等差看「差」，等比看「比」")
    c.t(70, 120, "等差数列：", 18, True, BLUE, "la")
    row1 = [(110, 150), (210, 150), (310, 150), (410, 150), (510, 150)]
    for i, (x, y) in enumerate(row1):
        c.circle(x, y, 18, (226, 236, 250), BLUE, 2)
        c.t(x, y, str(2 + i * 3), 15, True, dark, "mm")
    for i in range(4):
        c.arrow(row1[i][0] + 18, 150, row1[i + 1][0] - 18, 150, 2, BLUE, 9)
        c.t((row1[i][0] + row1[i + 1][0]) // 2, 138, "+d", 14, True, BLUE, "mb")
    c.t(70, 220, "等比数列：", 18, True, RED, "la")
    row2 = [(110, 270), (210, 270), (310, 270), (410, 270), (510, 270)]
    vals = ["2", "4", "8", "16", "32"]
    for i, (x, y) in enumerate(row2):
        c.circle(x, y, 18, (250, 232, 226), RED, 2)
        c.t(x, y, vals[i], 15, True, dark, "mm")
    for i in range(4):
        c.arrow(row2[i][0] + 18, 270, row2[i + 1][0] - 18, 270, 2, RED, 9)
        c.t((row2[i][0] + row2[i + 1][0]) // 2, 288, "×q", 14, True, RED, "mt")
    c.rich(W // 2, H - 42, "等差通项 a_{n}=a_{1}+(n−1)d；等比通项 a_{n}=a_{1}q^{n−1}", 17)
    return c




if __name__ == "__main__":
    save(build(), "数学_7.2_等差数列基本量")
