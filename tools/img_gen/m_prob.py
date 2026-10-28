# -*- coding: utf-8 -*-
"""古典概型 配图生成脚本。
单独运行：python m_prob.py  （只生成这一张，写入 app/src/main/assets/img/m_prob.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("古典概型", "等可能基本事件：P(A) = \\frac{有利事件数}{总事件数}")
    # 骰子
    c.rect(130, 130, 230, 230, "white", (40, 40, 40), 3, 10)
    for i, (dx, dy) in enumerate(((-32, -32), (32, -32), (-32, 32), (32, 32), (0, 0), (0, -64))):
        x, y = 180 + dx, 180 + dy
        if i < 5:
            c.circle(x, y, 8, dark, dark, 1)
    c.t(180, 252, "掷骰子", 15, True, dark, "mm")
    c.t(400, 130, "P(奇数)", 20, True, RED, "la")
    c.rich(400, 178, "= \\frac{3}{6} = \\frac{1}{2}", 26, True, RED, "ma")
    c.t(400, 210, "样本空间 {1,2,3,4,5,6}", 14, False, GREY, "la")
    c.t(400, 236, "事件 A = {1,3,5}", 14, False, GREY, "la")
    c.rich(W // 2, H - 42, "基本事件等可能且有限时，P(A) = m/n（m 为 A 的基本事件数）", 16)
    return c




if __name__ == "__main__":
    save(build(), "m_prob")
