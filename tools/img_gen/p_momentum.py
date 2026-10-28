# -*- coding: utf-8 -*-
"""一维碰撞与动量守恒 配图生成脚本。
单独运行：python p_momentum.py  （只生成这一张，写入 app/src/main/assets/img/p_momentum.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("一维碰撞与动量守恒", "碰撞前后系统总动量不变")
    y = 230
    c.line(70, y, 570, y, 3, GREY)
    c.t(84, y - 26, "碰前", 17, True, GREY, "la")
    # 碰撞前
    c.circle(150, y - 40, 34, (250, 236, 226), RED, 3)
    c.t(150, y - 40, "m_{1}", 15, True, RED, "mm")
    c.arrow(190, y - 40, 270, y - 40, 3, RED, 13)
    c.t(230, y - 58, "v_{1}", 17, True, RED, "mb")
    c.circle(450, y - 40, 30, (226, 240, 250), BLUE, 3)
    c.t(450, y - 40, "m_{2}", 15, True, BLUE, "mm")
    c.arrow(414, y - 40, 352, y - 40, 3, BLUE, 13)
    c.t(384, y - 58, "v_{2}", 17, True, BLUE, "mb")
    c.dash(320, y - 70, 320, y + 10, 2, (150, 150, 150))
    # 碰撞后
    c.t(84, y + 80, "碰后", 17, True, GREY, "la")
    c.arrow(150, y + 92, 235, y + 92, 3, RED, 13)
    c.t(195, y + 110, "v_{1}′", 17, True, RED, "mt")
    c.arrow(450, y + 92, 330, y + 92, 3, BLUE, 13)
    c.t(390, y + 110, "v_{2}′", 17, True, BLUE, "mt")
    c.rich(W // 2, H - 42, "m_{1}v_{1} + m_{2}v_{2} = m_{1}v_{1}′ + m_{2}v_{2}′", 18)
    return c




if __name__ == "__main__":
    save(build(), "p_momentum")
