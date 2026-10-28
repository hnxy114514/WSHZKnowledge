# -*- coding: utf-8 -*-
"""锌铜原电池 配图生成脚本。
单独运行：python c_cell.py  （只生成这一张，写入 app/src/main/assets/img/c_cell.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("锌铜原电池", "活泼金属作负极失电子，电子经导线流向正极")
    c.rect(120, 160, 540, 340, (232, 242, 250), (120, 150, 180), 2, 4)
    c.line(320, 160, 320, 340, 3, (120, 150, 180))
    c.rect(160, 176, 200, 340, (226, 226, 232), (110, 110, 120), 3, 3)
    c.rect(440, 176, 480, 340, (222, 210, 196), (150, 110, 60), 3, 3)
    c.t(180, 190, "Zn", 18, True, dark, "mt")
    c.t(460, 190, "Cu", 18, True, dark, "mt")
    c.line(180, 176, 180, 112, 3, dark)
    c.line(460, 176, 460, 112, 3, dark)
    c.line(180, 112, 460, 112, 3, dark)
    c.t(320, 130, "电流表 / 导线", 15, False, (90, 90, 90), "mb")
    c.arrow(288, 100, 232, 100, 2, RED, 10)
    c.t(224, 84, "e^{−}", 15, True, RED, "rm")
    c.t(250, 300, "负极（氧化）", 16, True, RED, "ma")
    c.t(410, 300, "正极（还原）", 16, True, BLUE, "ma")
    c.rich(W // 2, H - 40, "负极 Zn−2e^{−}=Zn^{2+} ，正极 2H^{+}+2e^{−}=H_{2}↑", 16)
    return c




if __name__ == "__main__":
    save(build(), "c_cell")
