# -*- coding: utf-8 -*-
"""主从复合句结构 配图生成脚本。
单独运行：python e_clause.py  （只生成这一张，写入 app/src/main/assets/img/e_clause.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("主从复合句结构", "从句必须有引导词，且用陈述语序")
    def node(x, y, w, lab, col, bold=True):
        c.rect(x - w / 2, y - 24, x + w / 2, y + 24, "white", col, 2, 6)
        c.t(x, y, lab, 15, bold, col, "mm")
    node(320, 140, 300, "主句：He said", BLUE)
    node(180, 240, 220, "宾语从句", GREEN)
    node(470, 240, 220, "定语从句", (170, 130, 40))
    node(180, 330, 280, "that he would come", dark, False)
    node(470, 330, 280, "which I told you", dark, False)
    c.line(320, 164, 200, 216, 2, GREY)
    c.line(320, 164, 450, 216, 2, GREY)
    c.arrow(180, 264, 180, 306, 2, GREY, 9)
    c.arrow(470, 264, 470, 306, 2, GREY, 9)
    c.rich(W // 2, H - 40, "宾语从句用 that/if/whether；定语从句关系词看先行词", 15)
    return c




if __name__ == "__main__":
    save(build(), "e_clause")
