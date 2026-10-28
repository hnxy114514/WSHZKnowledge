# -*- coding: utf-8 -*-
"""气体等温变化 p-V 图 配图生成脚本。
单独运行：python p_gas.py  （只生成这一张，写入 app/src/main/assets/img/p_gas.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("气体等温变化 p-V 图", "温度不变时 pV = 常量（双曲线）")
    px, py = coords(c, -0.4, 7.0, -0.4, 7.0, (78, 120, 596, 372))
    for k, col, lab in ((6.0, RED, "T_{1}"), (12.0, BLUE, "T_{2} 高温")):
        c.fn_curve(lambda v, k=k: k / v if v > 0.4 else None, 0.8, 6.8, px, py, 260, col, 3)
    c.t(px(1.1), py(6 / 1.1) - 16, "T_{1}", 17, True, RED, "ma")
    c.t(px(6.1), py(12 / 6.1) + 18, "T_{2} 高温", 17, True, BLUE, "ma")
    c.poly([(px(2), py(0)), (px(2), py(6 / 2)), (px(4), py(6 / 4)), (px(4), py(0))], (226, 234, 246), BLUE, 2)
    c.t(px(3.0), py(1.3), "面积 = pV", 16, True, BLUE, "ma")
    c.rich(W // 2, H - 40, "玻意耳定律 p_{1}V_{1} = p_{2}V_{2}（T 不变）", 17)
    return c




if __name__ == "__main__":
    save(build(), "物理_12.2_实验定律")
