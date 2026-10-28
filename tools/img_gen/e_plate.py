# -*- coding: utf-8 -*-
"""板块碰撞与褶皱山脉 配图生成脚本。
单独运行：python e_plate.py  （只生成这一张，写入 app/src/main/assets/img/e_plate.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("板块碰撞与褶皱山脉", "板块挤压使岩层弯曲隆起，形成高大山脉")
    base = 340
    c.rect(60, base, 580, base + 30, (232, 226, 214), (150, 130, 100), 2, 3)
    pts = []
    for i in range(0, 521, 4):
        x = 60 + i
        u = (x - 320) / 240.0
        y = base - 96 * math.exp(-u * u * 2.6)
        pts.append((x, y))
    c.d.line(pts, fill=(150, 110, 60), width=5)
    for k in (1, 2):
        pts2 = [(x, y + k * 22) for x, y in pts]
        c.d.line(pts2, fill=(176, 140, 90), width=2)
    c.arrow(110, base - 20, 250, base - 20, 3, RED, 12)
    c.arrow(530, base - 20, 390, base - 20, 3, BLUE, 12)
    c.t(180, base + 16, "板块挤压", 15, True, RED, "ma")
    c.t(460, base + 16, "板块挤压", 15, True, BLUE, "ma")
    c.t(320, base - 118, "褶皱山", 16, True, (150, 110, 60), "mb")
    c.rich(W // 2, H - 40, "消亡边界形成山脉、海沟；生长边界形成裂谷、海岭", 15)
    return c




if __name__ == "__main__":
    save(build(), "e_plate")
