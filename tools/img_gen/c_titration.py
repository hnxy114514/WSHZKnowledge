# -*- coding: utf-8 -*-
"""酸碱中和滴定曲线 配图生成脚本。
单独运行：python c_titration.py  （只生成这一张，写入 app/src/main/assets/img/c_titration.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("酸碱中和滴定曲线", "滴定突跃与指示剂选择")
    px, py = coords(c, -0.4, 14.0, -0.6, 14.6, (78, 120, 596, 372))
    def f(v):
        if v < 9.9:
            return 1.2 + 0.12 * v
        if v <= 10.1:
            return 1.2 + 0.12 * 9.9 + (v - 9.9) / 0.2 * 11.2
        return 12.2 + 0.12 * (v - 10.1)
    c.fn_curve(f, 0, 13.8, px, py, 400, RED)
    c.dash(px(0), py(7), px(13.8), py(7), 1, (170, 170, 170))
    c.t(px(1.2), py(7) + 18, "pH=7 中和点", 16, False, GREY, "la")
    c.dash(px(10), py(0), px(10), py(13.8), 1, (150, 150, 150))
    c.t(px(10) + 8, py(13.6), "V=10 mL", 16, False, GREY, "la")
    c.t(px(11.2), py(13.6), "突跃范围", 16, True, BLUE, "ma")
    c.rich(W // 2, H - 40, "强酸强碱滴定可用酚酞或甲基橙，误差 ≤ 0.1%", 16)
    return c




if __name__ == "__main__":
    save(build(), "化学_8.1_滴定")
