# -*- coding: utf-8 -*-
"""机械波的图象 配图生成脚本。
单独运行：python p_wave.py  （只生成这一张，写入 app/src/main/assets/img/p_wave.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("机械波的图象", "相邻波峰间距是一个波长 λ")
    px, py = coords(c, -0.3, 7.4, -1.8, 1.9, (78, 130, 596, 360))
    c.fn_curve(lambda v: math.sin(v), 0, 7.2, px, py, 320, BLUE)
    for k in (0, 2 * math.pi):
        c.dash(px(k), py(-1.7), px(k), py(1.7), 1, (185, 185, 185))
    c.arrow(px(0), py(-1.45), px(2 * math.pi), py(-1.45), 2, RED, 11)
    c.t(px(math.pi), py(-1.45) - 10, "λ", 20, True, RED, "mb")
    c.t(px(1.57), py(1) - 14, "波峰", 17, False, GREY, "mb")
    c.t(px(4.71), py(-1) + 30, "波谷", 17, False, GREY, "mt")
    c.rich(W // 2, H - 40, "v = λ/T = λf ，波的频率由波源决定", 17)
    return c




if __name__ == "__main__":
    save(build(), "物理_13.2_波的图象")
