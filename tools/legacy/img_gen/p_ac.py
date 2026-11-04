# -*- coding: utf-8 -*-
"""正弦式交流电 配图生成脚本。
单独运行：python p_ac.py  （只生成这一张，写入 app/src/main/assets/img/p_ac.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("正弦式交流电", "e = E_{m} sin ωt，周期 T，频率 f = \\frac{1}{T}")
    px, py = coords(c, -0.6, 10.4, -1.3, 1.3, (80, 122, 596, 360), "t", "e")
    c.fn_curve(lambda t: math.sin(t * 0.628), 0, 10.0, px, py, 300, RED, 4)
    c.dash(px(0), py(1.0), px(10.0), py(1.0), 1, GREY)
    c.t(px(2.5), py(1.16), "峰值 E_{m}", 16, True, RED, "mb")
    c.dash(px(0), py(-1.0), px(10.0), py(-1.0), 1, GREY)
    c.t(px(2.5), py(-1.18), "−E_{m}", 16, True, RED, "la")
    c.dash(px(10.0), py(0), px(10.0), py(1.02), 1, (150, 150, 150))
    c.t(px(10.0), py(0) + 22, "T", 16, True, BLUE, "ma")
    c.rich(W // 2, H - 42, "有效值 E = \\frac{E_{m}}{\\sqrt{2}} ≈ 0.707 E_{m}；ω = \\frac{2π}{T} = 2πf", 17)
    return c




if __name__ == "__main__":
    save(build(), "物理_11.1_正弦式交变电流")
