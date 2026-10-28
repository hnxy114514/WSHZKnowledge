# -*- coding: utf-8 -*-
"""理想变压器 配图生成脚本。
单独运行：python p_transformer.py  （只生成这一张，写入 app/src/main/assets/img/p_transformer.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("理想变压器", "电压比等于匝数比，功率不变")
    c.rect(210, 140, 440, 340, (238, 242, 246), (60, 70, 85), 3, 6)
    c.t(325, 236, "铁芯", 18, True, (60, 70, 85), "mm")
    # 原线圈
    for i in range(5):
        y = 168 + i * 34
        c.circle(196, y, 17, None, RED, 3)
    c.t(120, 150, "原线圈 n_{1}", 17, True, RED, "la")
    c.t(120, 178, "U_{1}", 17, True, RED, "la")
    # 副线圈
    for i in range(8):
        y = 152 + i * 24
        c.circle(454, y, 16, None, BLUE, 3)
    c.t(492, 150, "副线圈 n_{2}", 17, True, BLUE, "la")
    c.t(492, 178, "U_{2}", 17, True, BLUE, "la")
    c.rich(W // 2, H - 40, "\\frac{U_{1}}{U_{2}} = \\frac{n_{1}}{n_{2}} ，\\frac{I_{1}}{I_{2}} = \\frac{n_{2}}{n_{1}} ，P_{1} = P_{2}", 16)
    return c




if __name__ == "__main__":
    save(build(), "p_transformer")
