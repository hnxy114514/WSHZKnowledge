# -*- coding: utf-8 -*-
"""基因的分离定律 配图生成脚本。
单独运行：python b_meiosis.py  （只生成这一张，写入 app/src/main/assets/img/b_meiosis.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("基因的分离定律", "Aa × Aa 后代 3 显 : 1 隐，基因型 1:2:1")
    x0, y0, cw = 180, 176, 110
    heads = ["", "A", "a"]
    for i, h in enumerate(heads):
        if h:
            c.t(x0 + (i + 0.5) * cw, y0 - 34, h, 19, True, BLUE, "mm")
    for j, h in enumerate(heads):
        if not h:
            continue
        c.t(x0 - 40, y0 + (j - 0.5) * 92, h, 19, True, BLUE, "lm")
    grid = [["AA", "Aa"], ["Aa", "aa"]]
    for j in range(2):
        for i in range(2):
            x = x0 + i * cw
            y = y0 + j * 88
            fill = (226, 240, 232) if grid[j][i] != "aa" else (248, 230, 230)
            c.rect(x, y, x + cw, y + 88, fill, (90, 90, 90), 2, 6)
            c.t(x + cw / 2, y + 44, grid[j][i], 22, True, dark, "mm")
    c.t(W // 2, 376, "表现型：显性 3/4 ，隐性 1/4", 17, True, RED, "ma")
    c.rich(W // 2, H - 40, "测交 Aa × aa → 1:1，可验证基因型", 15)
    return c




if __name__ == "__main__":
    save(build(), "b_meiosis")
