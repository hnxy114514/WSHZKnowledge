# -*- coding: utf-8 -*-
"""对数函数图象 配图生成脚本。
单独运行：python m_log.py  （只生成这一张，写入 app/src/main/assets/img/m_log.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("对数函数图象", "定义域 x>0，过 (1,0)，a>1 递增")
    px, py = coords(c, -1.0, 5.4, -2.6, 3.2)
    c.fn_curve(lambda v: math.log(v, 2) if v > 0.02 else None, 0.02, 5.2, px, py, color=RED)
    c.fn_curve(lambda v: math.log(v, 0.5) if v > 0.02 else None, 0.02, 5.2, px, py, color=BLUE)
    c.dash(px(0.02), py(-2.6), px(0.02), py(3.2), 2, (170, 170, 170))
    c.circle(px(1), py(0), 5, GREEN, GREEN, 2)
    c.t(px(1) + 8, py(0) - 10, "(1,0)", 17, False, GREEN, "lb")
    c.t(px(3.4), py(0.65), "y = log_{2}x", 18, True, RED, "la")
    c.t(px(0.55), py(2.6), "y = log_{0.5}x", 18, True, BLUE, "lt")
    c.rich(W // 2, H - 42, "真数必须大于 0：log_{a}N 中 N>0；a>1 增，0<a<1 减", 18)
    return c




if __name__ == "__main__":
    save(build(), "数学_4.2_对数函数")
