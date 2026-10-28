# -*- coding: utf-8 -*-
"""细胞结构示意 配图生成脚本。
单独运行：python b_cell.py  （只生成这一张，写入 app/src/main/assets/img/b_cell.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("细胞结构示意", "各细胞器分工协作，线粒体供能、叶绿体光合")
    c.rect(90, 120, 550, 366, (240, 246, 252), (60, 110, 160), 4, 20)
    def num(x, y, n):
        c.circle(x, y, 13, "white", (40, 40, 40), 2)
        c.t(x, y, str(n), 14, True, (40, 40, 40), "mm")
    # 细胞核
    c.circle(300, 232, 56, (230, 238, 250), (44, 96, 178), 3)
    c.circle(300, 232, 20, (196, 210, 232), (44, 96, 178), 2)
    num(300, 232, 1)
    # 线粒体
    c.d.ellipse([382, 168, 470, 212], fill=(248, 228, 228), outline=RED, width=3)
    for i in range(3):
        c.line(392 + i * 26, 173, 392 + i * 26, 207, 2, RED)
    num(426, 190, 2)
    # 叶绿体
    c.d.ellipse([382, 274, 470, 318], fill=(226, 244, 232), outline=GREEN, width=3)
    for i in range(3):
        c.line(392 + i * 26, 279, 392 + i * 26, 313, 2, GREEN)
    num(426, 296, 3)
    # 内质网
    for i in range(3):
        c.line(128, 168 + i * 16, 208, 168 + i * 16, 3, (150, 120, 60))
    num(168, 220, 4)
    # 高尔基体
    for i in range(3):
        c.d.arc([128, 268 + i * 24, 196, 300 + i * 24, ], 200, 340, fill=(110, 90, 150), width=3)
    num(162, 252, 5)
    # 液泡
    c.d.ellipse([250, 300, 350, 356], fill=(236, 244, 250), outline=(90, 140, 170), width=2)
    num(300, 328, 6)
    c.rich(W // 2, 388, "① 细胞核  ② 线粒体  ③ 叶绿体", 14, True, (60, 60, 60), "ma")
    c.rich(W // 2, 414, "④ 内质网  ⑤ 高尔基体  ⑥ 液泡", 14, True, (60, 60, 60), "ma")
    c.rich(W // 2, H - 22, "植物细胞特有细胞壁、叶绿体、液泡；动物细胞有中心体", 13)
    return c




if __name__ == "__main__":
    save(build(), "b_cell")
