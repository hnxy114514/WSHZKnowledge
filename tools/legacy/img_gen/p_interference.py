# -*- coding: utf-8 -*-
"""双缝干涉 配图生成脚本。
单独运行：python p_interference.py  （只生成这一张，写入 app/src/main/assets/img/p_interference.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("双缝干涉", "两列相干光在屏上叠加形成明暗相间条纹")
    c.rect(70, 150, 110, 230, "white", (40, 40, 40), 2)
    c.line(90, 178, 90, 202, 2, dark)   # 单缝
    c.t(90, 168, "S_{0}", 16, True, dark, "mb")
    c.rect(140, 150, 170, 230, "white", (40, 40, 40), 2)
    c.line(148, 176, 148, 194, 2, dark)
    c.line(162, 176, 162, 194, 2, dark)
    c.t(155, 166, "S_{1} S_{2}", 15, True, dark, "mb")
    c.line(520, 150, 520, 230, 4, (200, 200, 205))
    c.t(520, 138, "屏", 16, True, dark, "mb")
    # 两条光路
    c.line(155, 185, 520, 176, 2, RED)
    c.line(155, 185, 520, 208, 2, BLUE)
    # 屏上条纹
    for i, yy in enumerate((156, 176, 208)):
        col = (240, 240, 245) if i % 2 else (230, 120, 120)
        c.rect(520, yy - 10, 560, yy + 10, col, (120, 120, 120), 1)
    c.t(540, 244, "明暗相间", 14, True, GREY, "ma")
    c.rich(W // 2, H - 42, "相邻亮纹间距 Δx = Lλ/d（L 为缝到屏距离，d 为双缝间距）", 17)
    return c




if __name__ == "__main__":
    save(build(), "物理_13.3_光的干涉")
