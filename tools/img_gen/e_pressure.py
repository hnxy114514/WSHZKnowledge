# -*- coding: utf-8 -*-
"""气压带与风带（侧视展开） 配图生成脚本。
单独运行：python e_pressure.py  （只生成这一张，写入 app/src/main/assets/img/e_pressure.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("气压带与风带（侧视展开）", "自北向南高压低压交替，风带斜向吹")
    bands = [(90, "极地高气压带", BLUE), (60, "副极地低气压带", RED),
             (30, "副热带高气压带", BLUE), (0, "赤道低气压带", RED),
             (-30, "副热带高气压带", BLUE), (-60, "副极地低气压带", RED),
             (-90, "极地高气压带", BLUE)]
    y0, bh, gap = 100, 26, 41
    for i, (lat, name, col) in enumerate(bands):
        y = y0 + i * gap
        c.rect(150, y, 350, y + bh, (238, 242, 250), col, 2, 4)
        c.t(145, y + bh / 2, name, 14, True, col, "rm")
    # 风带箭头画在两条气压带之间的空隙里（斜向表示风向）
    winds = [
        (0, "极地东风", "SW", (90, 130, 90)),
        (1, "西风带", "NE", (198, 58, 58)),
        (2, "东北信风", "SW", (44, 96, 178)),
        (3, "东南信风", "NW", (44, 96, 178)),
        (4, "西风带", "SE", (198, 58, 58)),
        (5, "极地东风", "NW", (90, 130, 90)),
    ]
    for i, label, d, col in winds:
        gy = y0 + i * gap + bh + (gap - bh) / 2
        if d == "SW":
            x1, y1, x2, y2 = 330, gy - 5, 268, gy + 5
        elif d == "NE":
            x1, y1, x2, y2 = 268, gy + 5, 330, gy - 5
        elif d == "NW":
            x1, y1, x2, y2 = 330, gy + 5, 268, gy - 5
        else:  # SE
            x1, y1, x2, y2 = 268, gy - 5, 330, gy + 5
        c.arrow(x1, y1, x2, y2, 3, col, 10)
        c.t(370, gy, label, 13, False, col, "lm")
    c.rich(W // 2, H - 40, "气压带、风带随太阳直射点季节移动；南、北半球风向相反", 14)
    return c




if __name__ == "__main__":
    save(build(), "e_pressure")
