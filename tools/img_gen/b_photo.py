# -*- coding: utf-8 -*-
"""光合作用过程 配图生成脚本。
单独运行：python b_photo.py  （只生成这一张，写入 app/src/main/assets/img/b_photo.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("光合作用过程", "光反应在类囊体薄膜，暗反应在叶绿体基质")
    c.rect(60, 140, 296, 356, (240, 248, 240), (38, 132, 88), 3, 8)
    c.rect(348, 140, 584, 356, (250, 246, 236), (170, 130, 40), 3, 8)
    c.t(178, 158, "光反应（类囊体）", 16, True, GREEN, "mm")
    c.t(466, 158, "暗反应（基质）", 16, True, (170, 130, 40), "mm")
    lines = ["H_{2}O 光解 → O_{2}", "ADP + Pi → ATP", "NADP^{+} → NADPH"]
    for i, t in enumerate(lines):
        c.rich(178, 208 + i * 38, t, 15, False, dark, "ma")
    lines2 = ["CO_{2} + C_{5} → 2C_{3}", "C_{3} 被还原 → (CH_{2}O)"]
    for i, t in enumerate(lines2):
        c.rich(466, 214 + i * 40, t, 14, False, dark, "ma")
    c.arrow(300, 244, 344, 244, 3, RED, 12)
    c.arrow(300, 264, 344, 264, 2, (200, 120, 60), 10)
    c.t(322, 232, "ATP", 13, True, RED, "mb")
    c.t(322, 276, "NADPH", 13, True, (200, 120, 60), "mt")
    c.rich(W // 2, H - 22, "光能 → ATP 和 NADPH 中的活跃化学能 → 有机物中稳定化学能", 13)
    return c




if __name__ == "__main__":
    save(build(), "b_photo")
