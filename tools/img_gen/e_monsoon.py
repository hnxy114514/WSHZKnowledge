# -*- coding: utf-8 -*-
"""季风环流 配图生成脚本。
单独运行：python e_monsoon.py  （只生成这一张，写入 app/src/main/assets/img/e_monsoon.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("季风环流", "海陆热力性质差异导致冬夏风向相反")
    c.rect(60, 150, 300, 360, (226, 240, 250), (120, 150, 180), 2, 4)
    c.t(180, 340, "海洋（夏季升温慢）", 15, True, (60, 100, 140), "mm")
    c.rect(340, 150, 580, 360, (238, 232, 218), (150, 130, 100), 2, 4)
    c.t(460, 340, "陆地（夏季升温快）", 15, True, (120, 100, 70), "mm")
    c.arrow(200, 210, 460, 210, 4, RED, 13)
    c.t(330, 196, "夏季风：海洋 → 陆地", 16, True, RED, "mb")
    c.arrow(460, 296, 200, 296, 4, BLUE, 13)
    c.t(330, 316, "冬季风：陆地 → 海洋", 16, True, BLUE, "mt")
    c.t(180, 152, "夏季：高压（凉）", 15, True, (60, 100, 140), "mm")
    c.t(460, 152, "夏季：低压（热）", 15, True, (120, 100, 70), "mm")
    c.rich(W // 2, H - 40, "夏季陆热低压、海洋高压；冬季相反，故风向随季节转换", 15)
    return c




if __name__ == "__main__":
    save(build(), "e_monsoon")
