# -*- coding: utf-8 -*-
"""水循环 配图生成脚本。
单独运行：python e_water.py  （只生成这一张，写入 app/src/main/assets/img/e_water.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("水循环", "蒸发、水汽输送、降水、径流构成完整循环")
    c.rect(60, 300, 580, 372, (226, 240, 250), (120, 150, 180), 2, 4)
    c.t(320, 336, "海洋", 16, True, (60, 100, 140), "mm")
    c.poly([(60, 300), (200, 230), (330, 292), (470, 208), (580, 292), (580, 300)], (236, 232, 220), (150, 130, 100), 3)
    c.t(470, 240, "陆地", 16, True, (120, 100, 70), "mb")
    c.arrow(180, 292, 180, 208, 3, RED, 12)
    c.t(188, 250, "蒸发", 15, True, RED, "lm")
    c.arrow(180, 200, 420, 172, 3, BLUE, 12)
    c.t(300, 168, "水汽输送", 15, True, BLUE, "mb")
    for x in (360, 420, 480):
        c.line(x, 186, x - 8, 250, 2, BLUE)
    c.t(512, 178, "降水", 15, True, BLUE, "lb")
    c.arrow(470, 286, 300, 300, 3, GREEN, 12)
    c.t(380, 296, "地表径流", 14, True, GREEN, "mb")
    c.rich(W // 2, H - 40, "人类影响最大的是地表径流；水循环使水资源不断更新", 15)
    return c




if __name__ == "__main__":
    save(build(), "e_water")
