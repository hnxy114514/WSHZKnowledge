# -*- coding: utf-8 -*-
"""棱柱与棱锥 配图生成脚本。
单独运行：python m_solid.py  （只生成这一张，写入 app/src/main/assets/img/m_solid.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("棱柱与棱锥", "体积 V = Sh，棱锥为 \\frac{1}{3}Sh")
    # 棱柱
    ox, oy = 170, 250
    d = 34
    top = [(ox, oy - 100), (ox + 120, oy - 100), (ox + 120, oy + 20), (ox, oy + 20)]
    c.dash(ox, oy - 100, ox - d, oy - 100 - d, 2, (150, 150, 150))
    c.dash(ox, oy + 20, ox - d, oy + 20 - d, 2, (150, 150, 150))
    c.dash(ox - d, oy - 100 - d, ox - d, oy + 20 - d, 2, (150, 150, 150))
    c.poly([(x - d, y - d) for x, y in top], None, BLUE, 3)
    c.poly(top, None, BLUE, 3)
    for x, y in top:
        c.line(x, y, x - d, y - d, 3, BLUE)
    c.t(ox + 40, oy + 46, "棱柱 V = Sh", 18, True, BLUE, "ma")
    # 棱锥
    bx, by = 430, 270
    base = [(bx, by), (bx + 120, by), (bx + 146, by - 40), (bx + 26, by - 40)]
    ap = (bx + 70, by - 118)
    c.poly(base, None, GREEN, 3)
    for p in base:
        c.line(ap[0], ap[1], p[0], p[1], 3, GREEN)
    c.t(bx + 60, by + 40, "棱锥 V = \\frac{1}{3}Sh", 18, True, GREEN, "ma")
    c.dash(ap[0], ap[1], ap[0], by - 20, 1, (150, 150, 150))
    c.rich(W // 2, H - 40, "柱体、锥体、台体体积：Sh 、\\frac{1}{3}Sh 、\\frac{(S+S′+\\sqrt{SS′})h}{3}", 17)
    return c




if __name__ == "__main__":
    save(build(), "m_solid")
