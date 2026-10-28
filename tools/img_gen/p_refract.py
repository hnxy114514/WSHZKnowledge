# -*- coding: utf-8 -*-
"""光的折射与全反射 配图生成脚本。
单独运行：python p_refract.py  （只生成这一张，写入 app/src/main/assets/img/p_refract.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("光的折射与全反射", "空气→玻璃：折射角小于入射角；光密→光疏可全反射")
    yb = 270
    # ---- 左半：折射（空气在上、玻璃在下）----
    O = (280, yb)
    th1 = math.radians(43)
    th2 = math.asin(math.sin(th1) / 1.5)
    c.line(60, yb, 390, yb, 3, (60, 60, 60))
    c.dash(O[0], yb - 150, O[0], yb + 140, 2, (170, 170, 170))
    L = 165
    src = (O[0] - math.sin(th1) * L, O[1] - math.cos(th1) * L)       # 入射光源（左上）
    refr = (O[0] + math.sin(th2) * 140, O[1] + math.cos(th2) * 140)  # 折射（右下，更靠法线）
    refl = (O[0] + math.sin(th1) * L, O[1] - math.cos(th1) * L)      # 反射（右上，与入射对称）
    c.arrow(src[0], src[1], O[0], O[1], 3, RED, 12)
    c.arrow(O[0], O[1], refr[0], refr[1], 3, RED, 12)
    c.arrow(O[0], O[1], refl[0], refl[1], 2, (200, 160, 160), 10)
    c.t(src[0] - 14, src[1] + 22, "入射光", 16, True, RED, "rb")
    c.t(refr[0] + 4, refr[1] - 6, "折射光", 16, True, RED, "lb")
    c.t(refl[0] + 4, refl[1] - 6, "反射光", 15, True, (200, 150, 150), "lb")
    # 入射角弧（法线向上 270° 到入射光 227°）
    r1 = 46
    c.d.arc([O[0]-r1, O[1]-r1, O[0]+r1, O[1]+r1], 227, 270, fill=dark, width=3)
    c.rich(O[0]-44, O[1]-22, "θ_{1}", 16, False, dark, "ma")
    # 折射角弧（法线向下 90° 到折射光 63°）
    r2 = 38
    c.d.arc([O[0]-r2, O[1]-r2, O[0]+r2, O[1]+r2], 63, 90, fill=dark, width=3)
    c.rich(O[0]+24, O[1]+18, "θ_{2}", 16, False, dark, "ma")
    c.rich(O[0], yb - 150, "θ_{1} > θ_{2}（空气→玻璃）", 16, True, RED, "ma")
    c.rich(168, 104, "n = \\frac{sinθ_{1}}{sinθ_{2}}", 18, True, dark, "ma")
    # ---- 右半：全反射（玻璃在下、空气在上）----
    O2 = (500, yb)
    ic = math.radians(50)
    Lc = 110
    c.line(410, yb, 580, yb, 3, (60, 60, 60))
    c.dash(O2[0], yb - 150, O2[0], yb + 140, 2, (170, 170, 170))
    src2 = (O2[0] - math.sin(ic) * Lc, O2[1] + math.cos(ic) * Lc)   # 入射（左下，玻璃中）
    refl2 = (O2[0] + math.sin(ic) * Lc, O2[1] + math.cos(ic) * Lc)  # 反射（右下，玻璃中）
    c.arrow(src2[0], src2[1], O2[0], O2[1], 3, BLUE, 12)
    c.arrow(O2[0], O2[1], refl2[0], refl2[1], 3, BLUE, 12)
    c.t(src2[0] - 6, src2[1] + 16, "入射光", 16, True, BLUE, "rb")
    c.t(refl2[0] + 4, refl2[1] + 8, "反射光", 16, True, BLUE, "la")
    r3 = 52
    c.d.arc([O2[0]-r3, O2[1]-r3, O2[0]+r3, O2[1]+r3], 90, 140, fill=(44, 96, 178), width=3)
    c.d.arc([O2[0]-r3, O2[1]-r3, O2[0]+r3, O2[1]+r3], 40, 90, fill=(44, 96, 178), width=3)
    c.t(O2[0], O2[1]+66, "i = i′", 16, True, BLUE, "mm")
    c.rich(O2[0], yb - 150, "全反射：i ≥ C", 17, True, BLUE, "ma")
    c.rich(W // 2, H - 40, "全反射临界角 sinC = \\frac{1}{n}（约 42°），仅光密→光疏时发生", 17)
    return c




if __name__ == "__main__":
    save(build(), "p_refract")
