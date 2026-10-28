# -*- coding: utf-8 -*-
"""物质的量核心换算 配图生成脚本。
单独运行：python c_mole.py  （只生成这一张，写入 app/src/main/assets/img/c_mole.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("物质的量核心换算", "以 n 为中心，四个方向对应四个公式")
    ox, oy = 320, 240
    c.circle(ox, oy, 52, (226, 236, 250), BLUE, 3)
    c.t(ox, oy, "n (mol)", 18, True, BLUE, "mm")
    nodes = [(320, 132, "m 质量", "n = \\frac{m}{M}"), (320, 348, "V 气体体积", "n = \\frac{V}{V_{m}}"),
             (166, 240, "N 微粒数", "n = \\frac{N}{N_{A}}"), (474, 240, "c 物质的量浓度", "n = c V")]
    for x, y, lab, formula in nodes:
        c.rect(x - 74, y - 30, x + 74, y + 30, "white", (60, 60, 60), 2, 6)
        c.t(x, y - 8, lab, 14, True, dark, "mm")
        c.t(x, y + 14, formula, 14, False, (90, 90, 90), "mm")
        c.arrow(ox, oy, x, y, 2, GREY, 10)
    c.rich(W // 2, H - 22, "N_{A} ≈ 6.02×10^{23} mol^{−1}，V_{m} = 22.4 L/mol（标准状况）", 14)
    return c




if __name__ == "__main__":
    save(build(), "化学_1.1_摩尔质量")
