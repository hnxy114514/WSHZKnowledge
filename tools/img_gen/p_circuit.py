# -*- coding: utf-8 -*-
"""串联与并联电路 配图生成脚本。
单独运行：python p_circuit.py  （只生成这一张，写入 app/src/main/assets/img/p_circuit.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("串联与并联电路", "串联分压、并联分流")
    # 串联
    def battery(x, y):
        c.line(x, y - 26, x, y + 26, 5, dark)
        c.line(x + 12, y - 15, x + 12, y + 15, 8, dark)
    c.line(120, 160, 120, 330, 3, dark)
    c.line(120, 330, 320, 330, 3, dark)
    c.line(320, 330, 320, 292, 3, dark)
    battery(320, 300)
    c.line(320, 268, 320, 160, 3, dark)
    c.line(320, 160, 120, 160, 3, dark)
    for i, x in enumerate((230, 285)):
        c.rect(x - 22, 148, x + 22, 174, "white", dark, 3, 3)
        c.t(x, 160, "R" + str(i + 1), 15, True, dark, "mm")
        c.line(120, 160, x - 22, 160, 3, dark)
        c.line(x + 22, 160, 320, 160, 3, dark)
    c.t(220, 352, "串联：I 相同，U = U_{1}+U_{2}", 17, True, BLUE, "ma")
    # 并联
    c.line(420, 160, 420, 330, 3, dark)
    c.line(420, 330, 560, 330, 3, dark)
    c.line(560, 330, 560, 300, 3, dark)
    battery(560, 305)
    c.line(560, 280, 560, 160, 3, dark)
    c.line(560, 160, 420, 160, 3, dark)
    for i, y in enumerate((206, 268)):
        c.line(420, y, 476, y, 3, dark)
        c.rect(476, y - 13, 508, y + 13, "white", dark, 3, 3)
        c.line(508, y, 560, y, 3, dark)
        c.t(492, y, "R" + str(i + 1), 15, True, dark, "mm")
    c.t(490, 352, "并联：U 相同，I = I_{1}+I_{2}", 17, True, GREEN, "ma")
    c.rich(W // 2, H - 40, "R_{串} = R_{1}+R_{2} ，\\frac{1}{R_{并}} = \\frac{1}{R_{1}} + \\frac{1}{R_{2}}", 17)
    return c




if __name__ == "__main__":
    save(build(), "p_circuit")
