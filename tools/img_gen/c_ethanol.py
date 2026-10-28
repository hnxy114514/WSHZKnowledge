# -*- coding: utf-8 -*-
"""乙醇与乙酸的结构 配图生成脚本。
单独运行：python c_ethanol.py  （只生成这一张，写入 app/src/main/assets/img/c_ethanol.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("乙醇与乙酸的结构", "官能团决定性质：羟基 −OH、羧基 −COOH")
    c.rect(90, 120, 550, 240, "white", (44, 96, 178), 2, 8)
    c.rich(320, 164, "乙醇 C_{2}H_{5}OH", 20, True, BLUE, "ma")
    c.rich(320, 200, "CH_{3} — CH_{2} — OH", 30, True, dark, "ma")
    c.t(430, 226, "羟基 −OH：与钠反应、被氧化成醛", 14, False, (90, 90, 90), "rm")
    c.rect(90, 272, 550, 392, "white", (198, 58, 58), 2, 8)
    c.rich(320, 316, "乙酸 CH_{3}COOH", 20, True, RED, "ma")
    c.rich(320, 352, "CH_{3} — COOH", 30, True, dark, "ma")
    c.t(430, 378, "羧基 −COOH：酸性、酯化反应", 14, False, (90, 90, 90), "rm")
    c.rich(W // 2, H - 22, "乙醇与乙酸在浓硫酸催化下生成乙酸乙酯", 14)
    return c




if __name__ == "__main__":
    save(build(), "c_ethanol")
