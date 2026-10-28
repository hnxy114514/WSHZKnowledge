# -*- coding: utf-8 -*-
"""议论文结构 配图生成脚本。
单独运行：python c_argue.py  （只生成这一张，写入 app/src/main/assets/img/c_argue.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("议论文结构", "引—议—联—结，层层递进")
    boxes = [(96, 190, 448, 238, "引：提出中心论点（开门见山）", BLUE),
             (96, 258, 448, 306, "议：分论点并列/递进 + 事例 + 分析", GREEN),
             (96, 326, 448, 374, "联：联系现实，正反对比", (170, 130, 40)),
             (200, 394, 344, 436, "结：回扣论点，升华", RED)]
    for x1, y1, x2, y2, lab, col in boxes:
        c.rect(x1, y1, x2, y2, "white", col, 2, 6)
        c.t((x1 + x2) / 2, (y1 + y2) / 2, lab, 15, True, col, "mm")
    for i in range(3):
        c.arrow(272, 238 + i * 68, 272, 256 + i * 68, 2, GREY, 9)
    c.rich(W // 2, 132, "分论点要并列或递进，不能交叉重复", 16, False, (90, 90, 90), "ma")
    return c




if __name__ == "__main__":
    save(build(), "c_argue")
