# -*- coding: utf-8 -*-
"""正态分布密度曲线 配图生成脚本。
单独运行：python m_normal.py  （只生成这一张，写入 app/src/main/assets/img/m_normal.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("正态分布密度曲线", "关于 x=μ 对称，σ 越小越瘦高")
    px, py = coords(c, -4.2, 4.2, 0, 0.95, (78, 130, 596, 356))
    for s, col, lab, tx, ty in ((1.0, RED, "σ=1", 2.5, 0.40), (0.5, BLUE, "σ=0.5", 1.6, 0.60)):
        k = 1 / (s * math.sqrt(2 * math.pi))
        c.fn_curve(lambda v, s=s, k=k: k * math.exp(-v * v / (2 * s * s)), -4.0, 4.0, px, py, 320, col, 3)
        c.t(px(tx), py(ty), lab, 17, True, col, "ma")
    c.dash(px(0), py(0), px(0), py(0.9), 2, (160, 160, 160))
    c.t(px(0) - 8, py(0.82), "x=μ", 17, False, GREY, "rm")
    c.rich(W // 2, H - 42, "P(μ−σ, μ+σ) ≈ 68.3% ，P(μ−2σ, μ+2σ) ≈ 95.4%", 17)
    return c




if __name__ == "__main__":
    save(build(), "m_normal")
