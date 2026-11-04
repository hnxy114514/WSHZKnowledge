# -*- coding: utf-8 -*-
"""反射弧的五个环节 配图生成脚本。
单独运行：python b_reflex.py  （只生成这一张，写入 app/src/main/assets/img/b_reflex.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("反射弧的五个环节", "感受器 → 传入神经 → 神经中枢 → 传出神经 → 效应器")
    y = 216
    labels = ["感受器", "传入神经", "神经中枢", "传出神经", "效应器"]
    xs = [96, 208, 320, 432, 544]
    for i, (x, lab) in enumerate(zip(xs, labels)):
        if i in (1, 3):
            c.d.ellipse([x - 26, y - 64, x + 26, y + 64], fill=(250, 246, 236), outline=(170, 130, 40), width=3)
            c.t(x, y - 80, lab, 15, True, (170, 130, 40), "mb")
        else:
            c.circle(x, y, 34, (232, 242, 250), (44, 96, 178), 3)
            c.t(x, y, lab, 15, True, (44, 96, 178), "mm")
    for i in range(4):
        c.arrow(xs[i] + 36, y, xs[i + 1] - 36, y, 3, (90, 90, 90), 12)
    c.t(W // 2, 320, "兴奋在突触处单向传递", 16, False, (90, 90, 90), "ma")
    c.rich(W // 2, H - 40, "神经调节基本方式：反射；结构基础：反射弧", 15)
    return c




if __name__ == "__main__":
    save(build(), "生物_7.2_神经调节")
