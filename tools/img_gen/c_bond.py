# -*- coding: utf-8 -*-
"""离子键与共价键 配图生成脚本。
单独运行：python c_bond.py  （只生成这一张，写入 app/src/main/assets/img/c_bond.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("离子键与共价键", "电子式：离子键用方括号与电荷，共价键用共用电子对")
    c.t(320, 132, "NaCl（离子键）", 20, True, RED, "mm")
    c.rich(320, 196, "Na^{+}  [ ∶Cl∶ ]^{−}", 34, True, dark, "ma")
    c.t(320, 232, "钠失 1 个电子，氯得 1 个电子", 15, False, (90, 90, 90), "ma")
    c.t(320, 300, "HCl（共价键）", 20, True, BLUE, "mm")
    c.rich(320, 360, "H ∶ Cl", 34, True, dark, "ma")
    c.t(320, 396, "氢氯各提供 1 个电子共用", 15, False, (90, 90, 90), "ma")
    c.rich(W // 2, H - 24, "离子键无方向性；共价键有方向性和饱和性", 14)
    return c




if __name__ == "__main__":
    save(build(), "c_bond")
