# -*- coding: utf-8 -*-
"""需求与供给曲线 配图生成脚本。
单独运行：python g_curve.py  （只生成这一张，写入 app/src/main/assets/img/g_curve.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("需求与供给曲线", "价格上升需求减少、供给增加，交点即均衡价格")
    px, py = coords(c, -0.4, 8.0, -0.4, 8.0, (78, 120, 596, 372), "Q 数量", "P 价格")
    c.fn_curve(lambda v: 8.2 - 0.95 * v, 0.4, 7.6, px, py, 50, RED)
    c.fn_curve(lambda v: 0.6 + 0.95 * v, 0.4, 7.6, px, py, 50, BLUE)
    c.t(px(6.6), py(8.2 - 0.95 * 6.6) - 20, "需求 D", 17, True, RED, "ma")
    c.t(px(6.6), py(0.6 + 0.95 * 6.6) + 26, "供给 S", 17, True, BLUE, "ma")
    c.dash(px(0), py(4.4), px(4.0), py(4.4), 1, (150, 150, 150))
    c.dash(px(4.0), py(0), px(4.0), py(4.4), 1, (150, 150, 150))
    c.circle(px(4.0), py(4.4), 6, GREEN, GREEN, 2)
    c.t(px(4.0) + 12, py(4.4) - 12, "E 均衡点", 16, True, GREEN, "lb")
    c.rich(W // 2, H - 40, "价格由价值决定并受供求影响；供不应求价格上涨", 15)
    return c




if __name__ == "__main__":
    save(build(), "政治_1.1_影响价格的因素")
