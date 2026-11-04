# -*- coding: utf-8 -*-
"""正弦函数图象 配图生成脚本。
单独运行：python m_trig.py  （只生成这一张，写入 app/src/main/assets/img/m_trig.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("正弦函数图象", "周期 2π、振幅 1、五点作图")
    px, py = coords(c, -7.2, 7.4, -1.9, 1.9, (78, 130, 596, 360))
    c.fn_curve(math.sin, -7.0, 7.0, px, py, 400, BLUE)
    for k in (-1, 0, 1):
        c.dash(px(k * 2 * math.pi), py(-1.7), px(k * 2 * math.pi), py(1.7), 1, (185, 185, 185))
        c.t(px(k * 2 * math.pi), py(-1.7) + 16, ("−2π" if k < 0 else ("2π" if k > 0 else "0")), 17, False, GREY, "mt")
    c.dash(px(0), py(1), px(7.0), py(1), 1, (210, 210, 210))
    c.t(px(1.57) - 4, py(1) - 6, "最大值 1", 18, False, RED, "mb")
    c.t(px(4.71), py(-1) + 22, "最小值 −1", 18, False, RED, "mt")
    c.rich(W // 2, H - 42, "y = sin x ：周期 T = 2π，值域 [−1, 1]，x = π/2 + 2kπ 取最大值", 18)
    return c




if __name__ == "__main__":
    save(build(), "数学_5.3_周期性")
